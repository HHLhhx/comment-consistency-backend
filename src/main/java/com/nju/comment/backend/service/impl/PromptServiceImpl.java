package com.nju.comment.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    @Value("classpath:prompts/prompt_user_comment_update.st")
    private Resource userCommentUpdateTemplate;

    @Value("classpath:prompts/prompt_user_comment_generate.st")
    private Resource userCommentGenerateTemplate;

    @Override
    public String buildUserPrompt(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        log.info("构建提示词开始");

        if (request.isRag()) {
            log.info("RAG启用，开始构建RAG示例");
            buildRagExample(request);
        }

        Map<String, Object> context = new HashMap<>();
        if (request.getOldComment() != null && !request.getOldComment().isEmpty()) {
            // 更新注释场景
            context.put("old_method", request.getOldMethod());
            context.put("new_method", request.getNewMethod());
            context.put("old_comment", request.getOldComment());

            try {
                PromptTemplate promptTemplate = new PromptTemplate(userCommentUpdateTemplate);
                Prompt prompt = promptTemplate.create(context);
                String contents = prompt.getContents();

                if (request.getRagExample() != null && !request.getRagExample().isEmpty()) {
                    // 将RAG示例添加到提示词开头
                    contents = "There are some examples:\n" + request.getRagExample() + "\n\n" + contents;
                }

                log.debug("注释更新提示词构建完成，内容:\n{}", contents);
                return contents;
            } catch (Exception e) {
                log.error("构建提示词失败", e);
                throw new ServiceException(ErrorCode.PROMPTS_BUILD_ERROR, e);
            }
        } else {
            // 生成注释场景
            context.put("new_method", request.getNewMethod());

            try {
                PromptTemplate promptTemplate = new PromptTemplate(userCommentGenerateTemplate);
                Prompt prompt = promptTemplate.create(context);
                log.debug("注释生成提示词构建完成，内容:\n{}", prompt.getContents());
                return prompt.getContents();
            } catch (Exception e) {
                log.error("构建提示词失败", e);
                throw new ServiceException(ErrorCode.PROMPTS_BUILD_ERROR, e);
            }
        }
    }


    private final VectorStore vectorStore;

    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/prompt_rag_example.txt")
    private Resource ragExampleTemplate;

    private void buildRagExample(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        long startTime = System.currentTimeMillis();
        String query = buildQueryForRAG(request);
        List<Document> top3 = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .build()
        );
        long endTime = System.currentTimeMillis();
        log.info("RAG检索完成，耗时：{}ms", endTime - startTime);

        String ragExamples = buildRagExamples(top3);
        request.setRagExample(ragExamples);
    }

    private String buildQueryForRAG(CommentRequest request) {
        if (request == null) return null;

        String oldMethod = request.getOldMethod();
        String newMethod = request.getNewMethod();
        String oldComment = request.getOldComment();

        return "what is the dst_javadoc for the following method change:\n" +
                "src_method: " + oldMethod + "\n" +
                "dst_method: " + newMethod + "\n" +
                "src_javadoc: " + oldComment + "\n";
    }

    private String buildRagExamples(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        List<String> examples = new ArrayList<>();
        PromptTemplate exampleTemplate = new PromptTemplate(ragExampleTemplate);

        for (Document document : documents) {
            if (document == null || document.getText() == null || document.getText().isEmpty()) {
                continue;
            }
            try {
                Map<String, Object> exampleContext = new HashMap<>();
                JsonNode node = objectMapper.readTree(document.getText());
                exampleContext.put("old_method", "\t" + node.path("src_method").asText(""));
                exampleContext.put("new_method", "\t" + node.path("dst_method").asText(""));
                exampleContext.put("old_comment", node.path("src_javadoc").asText(""));
                exampleContext.put("new_comment", node.path("dst_javadoc").asText(""));

                Prompt prompt = exampleTemplate.create(exampleContext);
                examples.add(prompt.getContents());
            } catch (Exception e) {
                log.warn("解析RAG示例失败，跳过该条向量结果", e);
            }
        }

        String delimiter = "\n----------------------------\n";
        return delimiter + String.join(delimiter, examples) + delimiter;
    }


    @Value("classpath:prompts/prompt_system_comment_update.txt")
    private Resource systemCommentUpdatePrompt;

    @Value("classpath:prompts/prompt_system_comment_generate.txt")
    private Resource systemCommentGeneratePrompt;

    @Override
    public String getSystemPrompt(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        if (request.getOldComment() != null && !request.getOldComment().isEmpty()) {
            // 更新注释场景
            try (InputStream is = systemCommentUpdatePrompt.getInputStream()) {
                return StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException ex) {
                log.error("读取系统提示词失败", ex);
                throw new ServiceException(ErrorCode.PROMPTS_READ_ERROR, ex);
            }
        } else {
            // 生成注释场景
            try (InputStream is = systemCommentGeneratePrompt.getInputStream()) {
                return StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException ex) {
                log.error("读取系统提示词失败", ex);
                throw new ServiceException(ErrorCode.PROMPTS_READ_ERROR, ex);
            }
        }
    }
}
