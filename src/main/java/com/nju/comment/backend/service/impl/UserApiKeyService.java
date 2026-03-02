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
 * 用户 API Key 管理服务
 * <p>
 * 负责加密存储、解密读取用户的 Ollama API Key。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserApiKeyService {

    private final UserRepository userRepository;
    private final ApiKeyEncryptor apiKeyEncryptor;

    /**
     * 保存（或更新）用户的 API Key
     */
    public void saveApiKey(String username, String plainApiKey) {
        User user = findUser(username);
        String encrypted = apiKeyEncryptor.encrypt(plainApiKey);
        user.setOllamaApiKey(encrypted);
        userRepository.save(user);
        log.info("用户 {} 的 API Key 已更新", username);
    }

    /**
     * 获取用户解密后的 API Key，未设置时返回 null
     */
    public String getDecryptedApiKey(String username) {
        User user = findUser(username);
        if (!StringUtils.hasText(user.getOllamaApiKey())) {
            return null;
        }
        return apiKeyEncryptor.decrypt(user.getOllamaApiKey());
    }

    /**
     * 检查用户是否已设置 API Key
     */
    public boolean hasApiKey(String username) {
        User user = findUser(username);
        return StringUtils.hasText(user.getOllamaApiKey());
    }

    /**
     * 删除用户的 API Key
     */
    public void deleteApiKey(String username) {
        User user = findUser(username);
        user.setOllamaApiKey(null);
        userRepository.save(user);
        log.info("用户 {} 的 API Key 已删除", username);
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
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND,
                        "用户不存在: " + username));
    }
}
