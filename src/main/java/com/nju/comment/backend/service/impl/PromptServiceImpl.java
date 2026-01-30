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

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    @Value("classpath:prompts/prompt_few_shot_query.st")
    private Resource templateName;

    @Override
    public String buildPrompt(CommentRequest request) {
        log.info("构建提示词开始");

        Map<String, Object> context = new HashMap<>();
        context.put("old_method", request.getOldMethod());
        context.put("new_method", request.getNewMethod());
        context.put("old_comment", request.getOldComment());

        try {
            PromptTemplate promptTemplate = new PromptTemplate(templateName);
            Prompt prompt = promptTemplate.create(context);
            log.debug("构建提示词完成，内容:\n{}", prompt.getContents());
            return prompt.getContents();
        } catch (Exception e) {
            log.error("构建提示词失败", e);
            throw new ServiceException(ErrorCode.PROMPTS_BUILD_ERROR, e);
        }
    }
}
