package com.nju.comment.backend.controller;

import com.nju.comment.backend.dto.request.LoginRequest;
import com.nju.comment.backend.dto.request.RegisterRequest;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.dto.response.AuthResponse;
import com.nju.comment.backend.service.impl.JwtService;
import com.nju.comment.backend.service.impl.TokenBlacklistService;
import com.nju.comment.backend.service.impl.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.ok(ApiResponse.success("注册成功", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(jwtService.getHeader());
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Date expiration = jwtService.extractExpiration(token);
            tokenBlacklistService.blacklist(token, expiration);
            log.info("用户登出成功: username={}", jwtService.extractUsername(token));
        }
        return ResponseEntity.ok(ApiResponse.success("已成功登出", null));
    }
}
