package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.LoginRequest;
import com.nju.comment.backend.dto.request.RegisterRequest;
import com.nju.comment.backend.dto.response.AuthResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.model.User;
import com.nju.comment.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       @Lazy AuthenticationManager authenticationManager, JwtService jwtService,
                       TokenBlacklistService tokenBlacklistService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ServiceException(ErrorCode.AUTH_USERNAME_EXISTS,
                    "用户名 '" + request.getUsername() + "' 已被注册");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername());
        log.info("用户注册成功: username={}", request.getUsername());
        return new AuthResponse(token, "注册成功");
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            String token = jwtService.generateToken(request.getUsername());
            log.info("用户登录成功: username={}", request.getUsername());
            return new AuthResponse(token, "登录成功");
        } catch (BadCredentialsException ex) {
            log.warn("登录失败，密码错误: username={}", request.getUsername());
            throw new ServiceException(ErrorCode.AUTH_LOGIN_FAILED, "用户名或密码错误");
        } catch (AuthenticationException ex) {
            log.warn("登录失败: username={}, error={}", request.getUsername(), ex.getMessage());
            throw new ServiceException(ErrorCode.AUTH_LOGIN_FAILED, "登录失败: " + ex.getMessage());
        }
    }

    public AuthResponse logout(HttpServletRequest request) {
        String authHeader = request.getHeader(jwtService.getHeader());
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Date expiration = jwtService.extractExpiration(token);
            tokenBlacklistService.blacklist(token, expiration);
            log.info("用户登出成功: username={}", jwtService.extractUsername(token));
        }
        return new AuthResponse(null, "已成功登出");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().getName())
                .build();
    }
}
