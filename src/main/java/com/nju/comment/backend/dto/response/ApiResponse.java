package com.nju.comment.backend.dto.response;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class ApiResponse<T> {

    private boolean success;
    private int code;
    private String message;
    private T data;
    private Long serverTime;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode(HttpStatus.OK.value());
        response.setMessage("success");
        response.setData(data);
        response.setServerTime(System.currentTimeMillis());
        return response;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode(HttpStatus.OK.value());
        response.setMessage(message);
        response.setData(data);
        response.setServerTime(System.currentTimeMillis());
        return response;
    }

    public static <T> ApiResponse<T> error(String message, int code) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        response.setData(null);
        response.setServerTime(System.currentTimeMillis());
        return response;
    }

    public static <T> ApiResponse<T> error(String message, HttpStatus status) {
        return error(message, status.value());
    }
}
