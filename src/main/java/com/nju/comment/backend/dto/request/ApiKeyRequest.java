package com.nju.comment.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyRequest {

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /**
     * OpenAI 协议兼容供应商的 Base URL，可选。
     * 为空时使用 {@code app.ai.openai.chat.base-url} 全局默认值。
     */
    @Size(max = 256, message = "Base URL 长度不能超过 256")
    @Pattern(
            regexp = "^$|^(https?://)[^\\s]+$",
            message = "Base URL 必须以 http:// 或 https:// 开头"
    )
    private String baseUrl;
}
