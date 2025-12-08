package com.nju.comment.backend.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class LLMServiceImplTest {

    @Autowired
    private  LLMServiceImpl llmService;

    @Test
    void local_ollama_health_and_generate_test() {
        assertNotNull(llmService, "LLMServiceImpl should be autowired successfully");

        boolean healthy;
        try {
            healthy = llmService.isServiceHealthy();
        } catch (Exception e) {
            fail("Health check threw an exception: " + e.getMessage());
            return;
        }
        assertTrue(healthy, "LLM service should be healthy");

        String prompt = "请生成一段简短的 JavaDoc 风格注释，描述一个用于将两个整数相加的方法。";
        String result = llmService.generateComment(prompt);

        assertNotNull(result, "LLM generate comment result should not be null");
        assertFalse(result.trim().isEmpty(), "LLM generate comment result should not be empty");
    }

    @Test
    void generate_comment_returns_result_for_valid_prompt() {
        ChatModel mockChatModel = mock(ChatModel.class);
        when(mockChatModel.call("valid prompt")).thenReturn("Generated Comment");

        LLMServiceImpl service = new LLMServiceImpl(mockChatModel);
        String result = service.generateComment("valid prompt");

        assertEquals("Generated Comment", result);
        verify(mockChatModel, times(1)).call("valid prompt");
    }

    @Test
    void generate_comment_throws_exception_for_invalid_prompt() {
        ChatModel mockChatModel = mock(ChatModel.class);
        when(mockChatModel.call("invalid prompt")).thenThrow(new RuntimeException("Error"));

        LLMServiceImpl service = new LLMServiceImpl(mockChatModel);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.generateComment("invalid prompt"));
        assertTrue(exception.getMessage().contains("LLM生成注释失败"));
        verify(mockChatModel, times(1)).call("invalid prompt");
    }

    @Test
    void is_service_healthy_returns_true_when_chat_model_responds() {
        ChatModel mockChatModel = mock(ChatModel.class);
        when(mockChatModel.call("hello")).thenReturn("response");

        LLMServiceImpl service = new LLMServiceImpl(mockChatModel);

        assertTrue(service.isServiceHealthy());
        verify(mockChatModel, times(1)).call("hello");
    }

    @Test
    void is_service_healthy_returns_false_when_chat_model_fails() {
        ChatModel mockChatModel = mock(ChatModel.class);
        when(mockChatModel.call("hello")).thenThrow(new RuntimeException("Error"));

        LLMServiceImpl service = new LLMServiceImpl(mockChatModel);

        assertFalse(service.isServiceHealthy());
        verify(mockChatModel, times(1)).call("hello");
    }
}
