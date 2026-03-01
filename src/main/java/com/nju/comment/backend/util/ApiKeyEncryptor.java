package com.nju.comment.backend.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具，用于安全存储用户 API Key
 * <p>
 * 每次加密生成随机 IV，密文格式: Base64(IV + ciphertext + tag)
 */
@Slf4j
public final class ApiKeyEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec keySpec;

    /**
     * @param hexKey 32字节(256位) 十六进制密钥
     */
    public ApiKeyEncryptor(String hexKey) {
        byte[] keyBytes = hexStringToBytes(hexKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("加密密钥必须为32字节(64个十六进制字符)");
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密明文 API Key
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes());

            // IV + 密文打包
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /**
     * 解密 API Key
     */
    public String decrypt(String cipherText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }
}
