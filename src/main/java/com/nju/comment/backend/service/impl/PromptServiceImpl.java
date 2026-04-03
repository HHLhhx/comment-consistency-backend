package com.nju.comment.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nju.comment.backend.dto.request.CommentReqTag;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.exception.*;
import com.nju.comment.backend.service.PromptService;
import com.nju.comment.backend.util.TextProcessUtil;
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
import org.springframework.web.client.ResourceAccessException;

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

        Map<String, Object> context = new HashMap<>();
        if (!CommentReqTag.GENERATE.equals(request.getTag())) {
            // 更新注释场景
            if (CommentReqTag.UPDATE_WITH_RAG.equals(request.getTag())) {
                log.info("RAG启用，开始构建RAG示例");
                buildRagExample(request);
            }

            context.put("old_method", TextProcessUtil.processMethod(request.getOldMethod()));
            context.put("new_method", TextProcessUtil.processMethod(request.getNewMethod()));
            context.put("old_comment", TextProcessUtil.processComment(request.getOldComment()));

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
                log.error("构建更新注释提示词失败", e);
                throw new PromptException(ErrorCode.PROMPT_BUILD_ERROR, "构建更新注释提示词失败", e);
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
                log.error("构建生成注释提示词失败", e);
                throw new PromptException(ErrorCode.PROMPT_BUILD_ERROR, "构建生成注释提示词失败", e);
            }
        }
    }


    private final VectorStore vectorStore;

    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/prompt_rag_example.txt")
    private Resource ragExampleTemplate;

    @Value("${app.vectorstore.embedding-max-input-chars:7000}")
    private int ragMaxQueryChars;

    private void buildRagExample(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        long startTime = System.currentTimeMillis();
        request.setRagExample("");

        try {
            int k = request.getRagExampleNum();
            if (k <= 0) {
                log.info("RAG降级：ragExampleNum<=0，回退为普通更新注释请求，requestId={}", request.getRequestId());
                return;
            }

            String query = buildQueryForRAG(request);
            if (isOverlongRagQuery(query)) {
                log.warn("RAG降级：检索查询过长，回退为普通更新注释请求，requestId={}, queryLength={}, maxAllowed={}",
                        request.getRequestId(), query.length(), Math.max(256, ragMaxQueryChars));
                return;
            }

            List<Document> topK = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(k)
                            .build()
            );
            long endTime = System.currentTimeMillis();
            log.info("RAG检索完成，耗时：{}ms", endTime - startTime);

            String ragExamples = buildRagExamples(topK);
            request.setRagExample(ragExamples);
        } catch (ResourceAccessException e) {
            if (isInterrupted(e)) {
                log.info("RAG检索在执行中被中断，耗时：{}ms，requestId={}",
                        System.currentTimeMillis() - startTime, request.getRequestId());
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INTERRUPTED, "RAG检索已取消", e);
            }
            log.warn("RAG降级：检索异常，回退为普通更新注释请求，requestId={}", request.getRequestId(), e);
        } catch (Exception e) {
            log.warn("RAG降级：检索异常，回退为普通更新注释请求，requestId={}", request.getRequestId(), e);
        }
    }

    private boolean isOverlongRagQuery(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }

        int maxChars = Math.max(256, ragMaxQueryChars);
        return query.codePointCount(0, query.length()) > maxChars;
    }

    private boolean isInterrupted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String buildQueryForRAG(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        String oldMethod = TextProcessUtil.processMethod(request.getOldMethod());
        String newMethod = TextProcessUtil.processMethod(request.getNewMethod());
        String oldComment = TextProcessUtil.processComment(request.getOldComment());

        ObjectNode jsonNodes = objectMapper.createObjectNode();
        jsonNodes.put("src_method", oldMethod);
        jsonNodes.put("dst_method", newMethod);
        jsonNodes.put("src_javadoc", oldComment);

        try {
            return objectMapper.writeValueAsString(jsonNodes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("构建RAG查询字符串失败", e);
            throw new PromptException(ErrorCode.PROMPT_BUILD_ERROR, "构建RAG查询字符串失败", e);
        }
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
                String srcMethod = readDocumentField(document, "src_method");
                String dstMethod = readDocumentField(document, "dst_method");
                String srcJavadoc = readDocumentField(document, "src_javadoc");
                String dstJavadoc = readDocumentField(document, "dst_javadoc");

                exampleContext.put("old_method", "\t" + srcMethod);
                exampleContext.put("new_method", "\t" + dstMethod);
                exampleContext.put("old_comment", srcJavadoc);
                exampleContext.put("new_comment", dstJavadoc);

                Prompt prompt = exampleTemplate.create(exampleContext);
                examples.add(prompt.getContents());
            } catch (Exception e) {
                log.warn("解析RAG示例失败，跳过该条向量结果", e);
            }
        }

        String delimiter = "\n--------------------------------------------------------\n";
        return delimiter + String.join(delimiter, examples) + delimiter;
    }

    private String readDocumentField(Document document, String fieldName) {
        Object metadataValue = document.getMetadata().get(fieldName);
        if (metadataValue != null) {
            if (fieldName.contains("method")) {
                return TextProcessUtil.processMethod(metadataValue.toString());
            } else if (fieldName.contains("javadoc")) {
                return TextProcessUtil.processComment(metadataValue.toString());
            }
        }

        try {
            JsonNode node = objectMapper.readTree(document.getText());
            return node.path(fieldName).asText("");
        } catch (Exception ignored) {
            return "";
        }
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
                log.error("读取更新注释系统提示词失败", ex);
                throw new PromptException(ErrorCode.PROMPT_TEMPLATE_READ_ERROR, "读取更新注释系统提示词失败", ex);
            }
        } else {
            // 生成注释场景
            try (InputStream is = systemCommentGeneratePrompt.getInputStream()) {
                return StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException ex) {
                log.error("读取生成注释系统提示词失败", ex);
                throw new PromptException(ErrorCode.PROMPT_TEMPLATE_READ_ERROR, "读取生成注释系统提示词失败", ex);
            }
        }
    }
}
