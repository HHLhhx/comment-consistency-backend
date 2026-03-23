package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Service
public class RequestCryptoService {

    private static final String PREFIX = "ENC:";

    private final PrivateKey privateKey;
    private final String publicKeyBase64;

    public RequestCryptoService(
            @Value("${app.security.request-decrypt-private-key:}") String privateKeyBase64,
            @Value("${app.security.request-encrypt-public-key:}") String configuredPublicKeyBase64)
            throws Exception {
        if (privateKeyBase64 != null && !privateKeyBase64.isBlank()) {
            this.privateKey = loadPrivateKey(privateKeyBase64);
            this.publicKeyBase64 = (configuredPublicKeyBase64 != null && !configuredPublicKeyBase64.isBlank())
                    ? configuredPublicKeyBase64
                    : derivePublicKeyFromPrivate(this.privateKey);
            return;
        }

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public String getPublicKeyBase64() {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new ServiceException(ErrorCode.SYSTEM_ERROR,
                    "请求加密公钥未配置，请设置 app.security.request-encrypt-public-key");
        }
        return publicKeyBase64;
    }

    public String decryptIfNeeded(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        String cipherText = value.substring(PREFIX.length());
        try {
            byte[] encrypted = Base64.getDecoder().decode(cipherText);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "敏感字段解密失败", ex);
        }
    }

    private PrivateKey loadPrivateKey(String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    private String derivePublicKeyFromPrivate(PrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivate)) {
            throw new ServiceException(ErrorCode.SYSTEM_ERROR,
                    "无法从当前私钥推导公钥，请配置 app.security.request-encrypt-public-key");
        }
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(rsaPrivate.getModulus(), rsaPrivate.getPublicExponent());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return Base64.getEncoder().encodeToString(keyFactory.generatePublic(publicKeySpec).getEncoded());
    }
}