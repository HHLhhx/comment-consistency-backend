package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.PromptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CommentExtendServiceImplTest {

    @MockitoBean
    private PromptService promptService;

    @MockitoBean
    private LLMService llmService;

    @Autowired
    private CommentExtendService commentService;

    @Test
    void should_return_empty_list_when_batch_request_is_null() throws ExecutionException, InterruptedException {
        CompletableFuture<List<CommentResponse>> responseFuture = commentService.batchGenerateComments(null);

        assertNotNull(responseFuture);
        List<CommentResponse> responses = responseFuture.get();
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void should_return_empty_list_when_batch_request_is_empty() throws ExecutionException, InterruptedException {
        CompletableFuture<List<CommentResponse>> responseFuture = commentService.batchGenerateComments(List.of());

        assertNotNull(responseFuture);
        List<CommentResponse> responses = responseFuture.get();
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void should_throw_exception_when_batch_request_exceeds_max_size() {
        List<CommentRequest> requests = List.of(
                new CommentRequest(), new CommentRequest(),
                new CommentRequest(), new CommentRequest(),
                new CommentRequest(), new CommentRequest(),
                new CommentRequest(), new CommentRequest(),
                new CommentRequest(), new CommentRequest(),
                new CommentRequest(), new CommentRequest()
        );

        CompletableFuture<List<CommentResponse>> future = commentService.batchGenerateComments(requests);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(ServiceException.class, ex.getCause());
        assertEquals("批量注释生成请求数量超过最大限制: 5", ex.getCause().getMessage());
    }

//    @Test
//    void should_process_batch_request_successfully() throws ExecutionException, InterruptedException {
//        CommentRequest request1 = CommentRequest.builder().oldCode("Request-1").build();
//        CommentRequest request2 = CommentRequest.builder().oldCode("Request-2").build();
//        String prompt1 = "Prompt-1";
//        String prompt2 = "Prompt-2";
//        String comment1 = "Comment-1";
//        String comment2 = "Comment-2";
//
//        when(promptService.buildPrompt(request1)).thenReturn(prompt1);
//        when(promptService.buildPrompt(request2)).thenReturn(prompt2);
//        when(llmService.generateComment(request1)).thenReturn(comment1);
//        when(llmService.generateComment(request2)).thenReturn(comment2);
//
//        List<CommentRequest> requests = List.of(request1, request2);
//        CompletableFuture<List<CommentResponse>> responseFuture = commentService.batchGenerateComments(requests);
//
//        assertNotNull(responseFuture);
//        List<CommentResponse> responses = responseFuture.get();
//        assertNotNull(responses);
//        assertEquals(2, responses.size());
//
//        Set<String> generated = responses.stream()
//                .map(CommentResponse::getGeneratedComment)
//                .collect(Collectors.toSet());
//
//        assertTrue(generated.contains(comment1));
//        assertTrue(generated.contains(comment2));
//
//        verify(promptService).buildPrompt(request1);
//        verify(promptService).buildPrompt(request2);
//        verify(llmService).generateComment(request1);
//        verify(llmService).generateComment(request2);
//    }
}
