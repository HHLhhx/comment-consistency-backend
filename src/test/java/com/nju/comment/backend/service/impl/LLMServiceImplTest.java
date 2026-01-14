package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.OllamaModelFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LLMServiceImplTest {

//    @Test
//    void generate_comment_returns_result_for_valid_prompt() {
//        ChatModel mockChatModel = mock(ChatModel.class);
//        when(mockChatModel.call("valid prompt")).thenReturn("Generated Comment");
//
//        OllamaModelFactory mockModelFactory = mock(OllamaModelFactory.class);
//
//        LLMServiceImpl service = new LLMServiceImpl(mockModelFactory, mockChatModel);
//        String result = service.generateComment("valid prompt");
//
//        assertEquals("Generated Comment", result);
//        verify(mockChatModel, times(1)).call("valid prompt");
//    }
//
//    @Test
//    void generate_comment_throws_exception_for_invalid_prompt() {
//        ChatModel mockChatModel = mock(ChatModel.class);
//        when(mockChatModel.call("invalid prompt")).thenThrow(new RuntimeException("Error"));
//
//        LLMServiceImpl service = new LLMServiceImpl(mockChatModel);
//
//        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.generateComment("invalid prompt"));
//        assertTrue(exception.getMessage().contains("LLM生成注释失败"));
//        verify(mockChatModel, times(1)).call("invalid prompt");
//    }
}
