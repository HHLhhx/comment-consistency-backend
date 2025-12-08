package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class PromptServiceTest {

    @Autowired
    private PromptServiceImpl promptService;

    @Test
    void build_prompt_generates_correct_prompt_with_all_fields() {
        CommentRequest request = CommentRequest.builder()
                .code("sample code")
                .existingComment("existing comment")
                .language("Java")
                .options(CommentRequest.GenerationOptions.builder()
                        .includeParams(true)
                        .includeReturn(true)
                        .includeExceptions(false)
                        .style("Javadoc")
                        .language("Chinese")
                        .build())
                .context(CommentRequest.Context.builder()
                        .className("SampleClass")
                        .packageName("com.example")
                        .relatedMethods(List.of(
                                new CommentRequest.MethodInfo("method1", "void method1()", "This is method1"),
                                new CommentRequest.MethodInfo("method2", "void method2()", "This is method2")
                        ))
                        .build())
                .build();

        String prompt = promptService.buildPrompt(request);

        assertNotNull(prompt);
        assertTrue(prompt.contains("sample code"));
        assertTrue(prompt.contains("existing comment"));
        assertTrue(prompt.contains("SampleClass"));
        assertTrue(prompt.contains("method1"));
        assertTrue(prompt.contains("method2"));
    }

    @Test
    void build_prompt_handles_null_context_gracefully() {
        CommentRequest request = CommentRequest.builder()
                .code("sample code")
                .existingComment(null)
                .language("Java")
                .options(CommentRequest.GenerationOptions.builder()
                        .includeParams(false)
                        .includeReturn(false)
                        .includeExceptions(false)
                        .style("Javadoc")
                        .language("English")
                        .build())
                .context(CommentRequest.Context.builder().build())
                .build();

        String prompt = promptService.buildPrompt(request);

        assertNotNull(prompt);
        assertTrue(prompt.contains("sample code"));
        assertFalse(prompt.contains("类名"));
        assertFalse(prompt.contains("方法名"));
    }
}
