package com.nju.comment.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Entity
@Data
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @Convert(converter = UserRoleConverter.class)
    private UserRole role = UserRole.USER;

    /**
     * 用户的 LLM API Key（OpenAI 协议兼容供应商，密文存储）。
     */
    @Column(name = "openai_api_key", length = 512)
    private String llmApiKey;

    /**
     * 用户的 LLM 服务 Base URL（OpenAI 协议兼容端点）。
     * <p>
     * 例如：
     * <ul>
     *     <li>https://api.openai.com</li>
     *     <li>https://api.deepseek.com</li>
     *     <li>https://dashscope.aliyuncs.com/compatible-mode</li>
     *     <li>https://api.moonshot.cn</li>
     * </ul>
     * 为空时回退到全局配置 {@code app.ai.openai.chat.base-url}。
     */
    @Column(name = "llm_base_url", length = 256)
    private String llmBaseUrl;

    @CreatedDate
    @Column(nullable = false)
    private Date createdTime;
}
