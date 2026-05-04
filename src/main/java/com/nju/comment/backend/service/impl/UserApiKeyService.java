package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.model.User;
import com.nju.comment.backend.repository.UserRepository;
import com.nju.comment.backend.util.ApiKeyEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户 LLM 凭证管理服务
 * <p>
 * 负责加密存储/解密读取用户的 OpenAI 协议兼容 API Key，
 * 同时管理用户自定义的 LLM Base URL（兼容 DeepSeek / Qwen / Moonshot 等）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserApiKeyService {

    private final UserRepository userRepository;
    private final ApiKeyEncryptor apiKeyEncryptor;

    /**
     * 保存（或更新）用户的 API Key 与可选的 Base URL。
     *
     * @param username    用户名
     * @param plainApiKey 明文 API Key
     * @param baseUrl     OpenAI 协议兼容供应商的 Base URL，{@code null} 或空表示沿用全局默认
     */
    public void saveApiKey(String username, String plainApiKey, String baseUrl) {
        User user = findUser(username);
        String encrypted = apiKeyEncryptor.encrypt(plainApiKey);
        user.setLlmApiKey(encrypted);
        // 仅当显式传入非空 baseUrl 时才覆盖；空字符串视为清空回退默认
        if (baseUrl != null) {
            user.setLlmBaseUrl(StringUtils.hasText(baseUrl) ? baseUrl.trim() : null);
        }
        userRepository.save(user);
        log.info("用户 {} 的 LLM 凭证已更新（baseUrl 是否设置: {}）",
                username, StringUtils.hasText(user.getLlmBaseUrl()));
    }

    /**
     * 获取用户解密后的 API Key，未设置时返回 null
     */
    public String getDecryptedApiKey(String username) {
        User user = findUser(username);
        if (!StringUtils.hasText(user.getLlmApiKey())) {
            return null;
        }
        return apiKeyEncryptor.decrypt(user.getLlmApiKey());
    }

    /**
     * 获取用户配置的 Base URL，未设置时返回 null（由调用方回退到全局默认）。
     */
    public String getBaseUrl(String username) {
        User user = findUser(username);
        return StringUtils.hasText(user.getLlmBaseUrl()) ? user.getLlmBaseUrl() : null;
    }

    /**
     * 检查用户是否已设置 API Key
     */
    public boolean hasApiKey(String username) {
        User user = findUser(username);
        return StringUtils.hasText(user.getLlmApiKey());
    }

    /**
     * 删除用户的 API Key 与 Base URL 配置
     */
    public void deleteApiKey(String username) {
        User user = findUser(username);
        user.setLlmApiKey(null);
        user.setLlmBaseUrl(null);
        userRepository.save(user);
        log.info("用户 {} 的 LLM 凭证已删除", username);
    }

    /**
     * 对 API Key 做脱敏处理，只显示前4位和后4位
     *
     * @param apiKey 明文 API Key
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "********" + apiKey.substring(apiKey.length() - 4);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND,
                        "用户不存在: " + username));
    }
}
