package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.LoginRequest;
import com.nju.comment.backend.dto.request.RegisterRequest;
import com.nju.comment.backend.dto.response.AuthResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.model.User;
import com.nju.comment.backend.model.UserCredential;
import com.nju.comment.backend.repository.UserCredentialRepository;
import com.nju.comment.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
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
import java.util.Optional;

@Service
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmailVerificationService emailVerificationService;

    public UserService(UserRepository userRepository,
                       UserCredentialRepository userCredentialRepository,
                       PasswordEncoder passwordEncoder,
                       @Lazy AuthenticationManager authenticationManager, JwtService jwtService,
                       TokenBlacklistService tokenBlacklistService,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ServiceException(ErrorCode.AUTH_USERNAME_EXISTS,
                    "用户名 '" + request.getUsername() + "' 已被注册");
        }
        if (userCredentialRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ServiceException(ErrorCode.AUTH_EMAIL_EXISTS,
                    "邮箱 '" + request.getEmail() + "' 已被注册");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ServiceException(ErrorCode.AUTH_PASSWORD_CONFIRM_MISMATCH, "两次输入密码不一致");
        }

        emailVerificationService.verifyRegisterCode(request.getEmail(), request.getEmailCode());

        User user = new User();
        user.setUsername(request.getUsername());
        User savedUser = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.setUser(savedUser);
        credential.setEmail(request.getEmail());
        credential.setPassword(passwordEncoder.encode(request.getPassword()));
        userCredentialRepository.save(credential);

        String token = jwtService.generateToken(savedUser.getUsername());
        log.info("用户注册成功: username={}", request.getUsername());
        return new AuthResponse(token, "注册成功");
    }

    public AuthResponse login(LoginRequest request) {
        String account = request.getAccount();
        try {
            String username = resolveLoginUsername(account);
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword())
            );

            String token = jwtService.generateToken(username);
            log.info("用户登录成功: account={}, username={}", account, username);
            return new AuthResponse(token, "登录成功");
        } catch (BadCredentialsException ex) {
            log.warn("登录失败，密码错误: account={}", account);
            throw new ServiceException(ErrorCode.AUTH_LOGIN_FAILED, "用户名/邮箱或密码错误");
        } catch (AuthenticationException ex) {
            log.warn("登录失败: account={}, error={}", account, ex.getMessage());
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
        UserCredential credential = userCredentialRepository.findWithUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        User user = credential.getUser();
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(credential.getPassword())
                .roles(user.getRole().getName())
                .build();
    }

    private String resolveLoginUsername(String account) {
        UserCredential credential = findCredentialByAccount(account)
                .orElseThrow(() -> new ServiceException(ErrorCode.AUTH_LOGIN_FAILED, "用户名/邮箱或密码错误"));
        return credential.getUser().getUsername();
    }


    private Optional<UserCredential> findCredentialByAccount(String account) {
        if (looksLikeEmail(account)) {
            return userCredentialRepository.findWithUserByEmail(account)
                .or(() -> userCredentialRepository.findWithUserByUsername(account));
        }
        return userCredentialRepository.findWithUserByUsername(account)
            .or(() -> userCredentialRepository.findWithUserByEmail(account));
    }

    private boolean looksLikeEmail(String account) {
        return account != null && account.contains("@");
    }
}
