package com.nju.comment.backend.controller;

import com.nju.comment.backend.dto.request.ApiKeyRequest;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.service.impl.UserApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户设置控制器
 * <p>
 * 管理用户个人配置，当前包括 Ollama API Key 的增删改查。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserSettingsController {

    private final UserApiKeyService userApiKeyService;

    /**
     * 保存或更新 API Key
     */
    @PutMapping("/api-key")
    public ResponseEntity<ApiResponse<Void>> saveApiKey(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ApiKeyRequest request) {
        userApiKeyService.saveApiKey(userDetails.getUsername(), request.getApiKey());
        return ResponseEntity.ok(ApiResponse.success("API Key 已保存", null));
    }

    /**
     * 检查是否已设置 API Key（不返回明文）
     */
    @GetMapping("/api-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkApiKey(
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean hasKey = userApiKeyService.hasApiKey(userDetails.getUsername());
        String maskedKey = null;
        if (hasKey) {
            String decrypted = userApiKeyService.getDecryptedApiKey(userDetails.getUsername());
            maskedKey = maskApiKey(decrypted);
        }
        Map<String, Object> data = Map.of(
                "configured", hasKey,
                "maskedKey", maskedKey != null ? maskedKey : ""
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/api-key")
    public ResponseEntity<ApiResponse<Void>> deleteApiKey(
            @AuthenticationPrincipal UserDetails userDetails) {
        userApiKeyService.deleteApiKey(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("API Key 已删除", null));
    }

    /**
     * 对 API Key 做脱敏处理，只显示前4位和后4位
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
