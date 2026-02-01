package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PromptServiceTest {

    @Autowired
    private PromptServiceImpl promptService;

    @Test
    void build_prompt_generates_correct_prompt_with_all_fields() {
        CommentRequest request = CommentRequest.builder()
                .oldMethod("sample code")
                .oldComment("existing comment")
                .newMethod("new code")
                .build();

        String prompt = promptService.buildUserPrompt(request);

        assertNotNull(prompt);
        assertTrue(prompt.contains("sample code"));
        assertTrue(prompt.contains("existing comment"));
        assertTrue(prompt.contains("new code"));
    }
}
