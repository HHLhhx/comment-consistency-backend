package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentBaseServiceImplTest {

    @Mock
    private PromptService promptService;

    @Mock
    private LLMService llmService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private CommentBaseServiceImpl commentService;

    private CommentRequest request;

    @BeforeEach
    void setup() {
        request = CommentRequest.builder().code("test code").build();
    }

    @Test
    void should_generate_comment_successfully() throws ExecutionException, InterruptedException {
        String prompt = "Generated Prompt";
        String generatedComment = "Generated Comment";
        when(promptService.buildPrompt(request)).thenReturn(prompt);
        when(llmService.generateComment(prompt)).thenReturn(generatedComment);
        when(llmService.getChatModelName()).thenReturn("TestModel");

        CompletableFuture<CommentResponse> responseFuture = commentService.generateComment(request);

        assertNotNull(responseFuture);
        CommentResponse response = responseFuture.get();
        assertTrue(response.isSuccess());
        assertEquals(generatedComment, response.getGeneratedComment());
        assertEquals("TestModel", response.getModelUsed());
        verify(promptService).buildPrompt(request);
        verify(llmService).generateComment(prompt);
    }

    @Test
    void should_handle_exception_during_comment_generation() throws ExecutionException, InterruptedException {
        String prompt = "Generated Prompt";
        when(promptService.buildPrompt(request)).thenReturn(prompt);
        when(llmService.generateComment(prompt)).thenThrow(new RuntimeException("Generation Error"));

        CompletableFuture<CommentResponse> responseFuture = commentService.generateComment(request);

        assertNotNull(responseFuture);
        CommentResponse response = responseFuture.get();
        assertFalse(response.isSuccess());
        assertEquals("Generation Error", response.getErrorMessage());
        verify(promptService).buildPrompt(request);
        verify(llmService).generateComment(prompt);
    }
}
