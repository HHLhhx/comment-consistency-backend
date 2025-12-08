package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final ChatModel ollamaChatModel;

    @Override
    public String generateComment(String prompt) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("调用LLM生成注释，prompt长度：{}", prompt.length());

            String result = ollamaChatModel.call(prompt);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("LLM生成注释完成，耗时：{} ms，内容：\n{}", duration, result);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("LLM生成注释失败，耗时：{} ms", duration);
            throw new RuntimeException("LLM生成注释失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isServiceHealthy() {
        try {
            return  ollamaChatModel.call("hello") != null;
        } catch (Exception e) {
            log.warn("LLM服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getChatModelName() {
        return ollamaChatModel.getDefaultOptions().getModel();
    }
}
