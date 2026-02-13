package com.nju.comment.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {

    private String oldMethod;

    private String oldComment;

    @NotBlank(message = "newMethod 不能为空")
    private String newMethod;

    private String modelName;

    @NotBlank(message = "requestId 不能为空")
    private String clientRequestId;

    @Min(value = 1, message = "timeoutMs 必须大于 0")
    private Long timeoutMs;

    private boolean rag;

    private String ragExample;
}
