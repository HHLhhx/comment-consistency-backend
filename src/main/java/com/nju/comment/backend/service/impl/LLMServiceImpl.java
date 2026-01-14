package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.OllamaModelFactory;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final OllamaModelFactory ollamaModelFactory;

    private final PromptService promptService;

    @Override
    public String generateComment(CommentRequest request) {
        long startTime = System.currentTimeMillis();

        ChatClient client = ollamaModelFactory.getModel(request.getModelName());
        try {
            log.info("调用LLM生成注释");

            String prompt = promptService.buildPrompt(request);
            String result = client.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            log.debug("LLM生成注释完成，耗时：{}ms，内容：\n{}", duration, result);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("LLM生成注释失败，耗时：{}ms", duration, e);
            throw new ServiceException(ErrorCode.LLM_SERVICE_ERROR, e);
        }
    }

    @Override
    public List<String> getAvailableModels() {
        return ollamaModelFactory.getAvailableModels();
    }
}
