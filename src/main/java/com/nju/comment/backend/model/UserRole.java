package com.nju.comment.backend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum UserRole {
    USER("USER"),
    ADMIN("ADMIN");

    private final String name;

    public static UserRole convert(String role) {
        if (role == null) return null;

        return Stream.of(values())
                .filter(bean -> bean.getName().equals(role))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的用户角色: " + role));
    }
}
