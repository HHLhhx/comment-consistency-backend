package com.nju.comment.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApiKeyRequest {

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;
}
