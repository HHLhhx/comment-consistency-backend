package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.LLMService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentBaseServiceImplTest {

    @Mock
    private LLMService llmService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private CommentBaseServiceImpl commentService;

    private CommentRequest request;

    @BeforeEach
    void setup() {
        request = CommentRequest.builder()
                .oldMethod("test code")
                .modelName("TestModel")
                .build();
    }

    @Test
    void should_generate_comment_successfully() throws ExecutionException, InterruptedException {
        String generatedComment = "Generated Comment";
        when(llmService.generateComment(request)).thenReturn(generatedComment);

        CompletableFuture<CommentResponse> responseFuture = commentService.generateComment(request);

        assertNotNull(responseFuture);
        CommentResponse response = responseFuture.get();
        assertTrue(response.isSuccess());
        assertEquals(generatedComment, response.getGeneratedComment());
        assertEquals("TestModel", response.getModelUsed());
        verify(llmService).generateComment(request);
    }
}
