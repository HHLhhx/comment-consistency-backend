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
        if (request.getOldComment() != null && !request.getOldComment().isEmpty()) {
            // 更新注释场景
            context.put("old_method", request.getOldMethod());
            context.put("new_method", request.getNewMethod());
            context.put("old_comment", request.getOldComment());

            try {
                PromptTemplate promptTemplate = new PromptTemplate(userCommentUpdateTemplate);
                Prompt prompt = promptTemplate.create(context);
                log.debug("注释更新提示词构建完成，内容:\n{}", prompt.getContents());
                return prompt.getContents();
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
            try(InputStream is = systemCommentUpdatePrompt.getInputStream()) {
                return StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException ex) {
                log.error("读取系统提示词失败", ex);
                throw new ServiceException(ErrorCode.PROMPTS_READ_ERROR, ex);
            }
        } else {
            // 生成注释场景
            try(InputStream is = systemCommentGeneratePrompt.getInputStream()) {
                return StreamUtils.copyToString(is, Charset.defaultCharset());
            } catch (IOException ex) {
                log.error("读取系统提示词失败", ex);
                throw new ServiceException(ErrorCode.PROMPTS_READ_ERROR, ex);
            }
        }
    }
}
