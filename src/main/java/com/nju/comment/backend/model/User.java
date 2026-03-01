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

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Convert(converter = UserRoleConverter.class)
    private UserRole role = UserRole.USER;

    /**
     * 用户的 Ollama API Key（AES-GCM 加密存储）
     */
    @Column(length = 512)
    private String ollamaApiKey;

    @CreatedDate
    @Column(nullable = false)
    private Date createdTime;
}
