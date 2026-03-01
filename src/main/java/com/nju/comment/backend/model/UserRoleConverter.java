package com.nju.comment.backend.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {
    @Override
    public String convertToDatabaseColumn(UserRole userRole) {
        return userRole.getName();
    }

    @Override
    public UserRole convertToEntityAttribute(String role) {
        return UserRole.convert(role);
    }
}