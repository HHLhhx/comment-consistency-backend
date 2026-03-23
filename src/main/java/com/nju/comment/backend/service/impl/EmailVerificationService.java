package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final long CODE_TTL_MINUTES = 5;
    private static final long SEND_COOLDOWN_SECONDS = 60;
    private static final String EMAIL_CODE_KEY_PREFIX = "auth:email:code:";
    private static final String EMAIL_COOLDOWN_KEY_PREFIX = "auth:email:cooldown:";

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserCredentialRepository userCredentialRepository;

    @Value("${spring.mail.username:}")
    private String sender;

    public void sendRegisterCode(String email) {
        if (userCredentialRepository.findByEmail(email).isPresent()) {
            throw new ServiceException(ErrorCode.AUTH_EMAIL_EXISTS, "邮箱 '" + email + "' 已被注册");
        }

        String cooldownKey = EMAIL_COOLDOWN_KEY_PREFIX + email;
        if (redisTemplate.hasKey(cooldownKey)) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED, "验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode();
        redisTemplate.opsForValue().set(EMAIL_CODE_KEY_PREFIX + email, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(cooldownKey, "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Comment Consistency 注册验证码");
            message.setText("您本次注册验证码为：" + code + "，" + CODE_TTL_MINUTES + "分钟内有效。");
            if (sender != null && !sender.isBlank()) {
                message.setFrom(sender);
            }
            mailSender.send(message);
        } catch (Exception ex) {
            throw new ServiceException(ErrorCode.AUTH_EMAIL_SEND_FAILED, "验证码发送失败，请稍后重试", ex);
        }
    }

    public void verifyRegisterCode(String email, String code) {
        Object cacheCode = redisTemplate.opsForValue().get(EMAIL_CODE_KEY_PREFIX + email);
        if (cacheCode == null || !cacheCode.equals(code)) {
            throw new ServiceException(ErrorCode.AUTH_EMAIL_CODE_INVALID, "邮箱验证码错误或已过期");
        }
        redisTemplate.delete(EMAIL_CODE_KEY_PREFIX + email);
    }

    private String generateCode() {
        int num = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(num);
    }
}