package com.nju.comment.backend.controller;

import com.nju.comment.backend.dto.request.ApiKeyRequest;
import com.nju.comment.backend.dto.response.ApiKeyInfoResponse;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.service.impl.RequestCryptoService;
import com.nju.comment.backend.service.impl.UserApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户设置控制器
 * <p>
 * 管理用户个人配置，当前包括 OpenAI 协议兼容的 LLM API Key 与 Base URL 的增删改查。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserSettingsController {

    private final UserApiKeyService userApiKeyService;
    private final RequestCryptoService requestCryptoService;

    @PutMapping("/api-key")
    public ResponseEntity<ApiResponse<Void>> saveApiKey(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ApiKeyRequest request
    ) {
        String plainApiKey = requestCryptoService.decryptIfNeeded(request.getApiKey());
        userApiKeyService.saveApiKey(userDetails.getUsername(), plainApiKey, request.getBaseUrl());
        return ResponseEntity.ok(ApiResponse.success("API 配置已保存", null));
    }

    @GetMapping("/api-key")
    public ResponseEntity<ApiResponse<ApiKeyInfoResponse>> checkApiKey(@AuthenticationPrincipal UserDetails userDetails) {
        boolean hasKey = userApiKeyService.hasApiKey(userDetails.getUsername());
        String maskKey = null;
        if (hasKey) {
            String apiKey = userApiKeyService.getDecryptedApiKey(userDetails.getUsername());
            maskKey = userApiKeyService.maskApiKey(apiKey);
        }
        String baseUrl = userApiKeyService.getBaseUrl(userDetails.getUsername());
        ApiKeyInfoResponse apiInfo = ApiKeyInfoResponse.builder().apiKey(maskKey).baseUrl(baseUrl).build();
        return ResponseEntity.ok(ApiResponse.success("API 配置信息", apiInfo));
    }

    @DeleteMapping("/api-key")
    public ResponseEntity<ApiResponse<Void>> deleteApiKey(@AuthenticationPrincipal UserDetails userDetails) {
        userApiKeyService.deleteApiKey(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("API 配置已删除", null));
    }
}
