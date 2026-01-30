package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    @Value("classpath:prompts/prompt_system_update_comment.txt")
    private Resource updateCommentSystemPromptResource;

    @Value("classpath:prompts/prompt_system_generate_comment.txt")
    private Resource generateCommentSystemPromptResource;

    @Override
    public String getSystemPrompt(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        String systemPrompt;
        if (request.getOldComment() == null || request.getOldComment().isBlank()) {
            try (InputStream is = generateCommentSystemPromptResource.getInputStream()) {
                systemPrompt = StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException e) {
                throw new ServiceException(ErrorCode.PROMPTS_TEMPLATE_ERROR, e);
            }
        } else {
            try (InputStream is = updateCommentSystemPromptResource.getInputStream()) {
                systemPrompt = StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException e) {
                throw new ServiceException(ErrorCode.PROMPTS_TEMPLATE_ERROR, e);
            }
        }

        return systemPrompt;
    }

    @Value("classpath:prompts/prompt_user_update_comment.st")
    private Resource updateCommentUserPromptResource;

    @Value("classpath:prompts/prompt_user_generate_comment.st")
    private Resource generateCommentUserPromptResource;

    @Override
    public String buildUserPrompt(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        log.info("构建提示词开始");

        Map<String, Object> context = new HashMap<>();

        if (request.getOldComment() == null || request.getOldComment().isBlank()) {
            context.put("old_method", request.getOldMethod());
            context.put("new_method", request.getNewMethod());
            context.put("old_comment", request.getOldComment());

            try {
                PromptTemplate promptTemplate = new PromptTemplate(generateCommentUserPromptResource);
                Prompt prompt = promptTemplate.create(context);
                log.debug("构建注释生成提示词完成，内容:\n{}", prompt.getContents());
                return prompt.getContents();
            } catch (Exception e) {
                log.error("构建提示词失败", e);
                throw new ServiceException(ErrorCode.PROMPTS_BUILD_ERROR, e);
            }
        } else {
            context.put("new_method", request.getNewMethod());

            try {
                PromptTemplate promptTemplate = new PromptTemplate(updateCommentUserPromptResource);
                Prompt prompt = promptTemplate.create(context);
                log.debug("构建注释更新提示词完成，内容:\n{}", prompt.getContents());
                return prompt.getContents();
            } catch (Exception e) {
                log.error("构建提示词失败", e);
                throw new ServiceException(ErrorCode.PROMPTS_BUILD_ERROR, e);
            }
        }
    }
}
