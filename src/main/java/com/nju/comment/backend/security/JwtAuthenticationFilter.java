package com.nju.comment.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.service.impl.JwtService;
import com.nju.comment.backend.service.impl.TokenBlacklistService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String SECURITY_CONTEXT_ATTR = "JWT_SECURITY_CONTEXT";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 不跳过异步派发请求，确保 CompletableFuture 异步完成时
     * 能在 ASYNC dispatch 中恢复 SecurityContext
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 异步派发时，从 request attribute 恢复 SecurityContext
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            SecurityContext savedContext = (SecurityContext) request.getAttribute(SECURITY_CONTEXT_ATTR);
            if (savedContext != null && savedContext.getAuthentication() != null) {
                SecurityContextHolder.setContext(savedContext);
            }
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            // 检查令牌是否在黑名单中
            if (tokenBlacklistService.isBlacklisted(token)) {
                log.warn("请求使用了已失效的令牌: uri={}", request.getRequestURI());
                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorCode.AUTH_TOKEN_BLACKLISTED.getMessage(), ErrorCode.AUTH_TOKEN_BLACKLISTED.getCode());
                return;
            }

            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            // 将 SecurityContext 保存到 request attribute，供异步派发时恢复
            request.setAttribute(SECURITY_CONTEXT_ATTR, SecurityContextHolder.getContext());

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            log.warn("JWT令牌已过期: uri={}", request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_EXPIRED.getMessage(), ErrorCode.AUTH_TOKEN_EXPIRED.getCode());
        } catch (JwtException ex) {
            log.warn("无效的JWT令牌: uri={}, error={}", request.getRequestURI(), ex.getMessage());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID.getMessage(), ErrorCode.AUTH_TOKEN_INVALID.getCode());
        } catch (Exception ex) {
            log.error("JWT认证过程中发生异常: uri={}", request.getRequestURI(), ex);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID.getMessage(), ErrorCode.AUTH_TOKEN_INVALID.getCode());
        }
    }

    /**
     * 向响应写入统一格式的错误信息
     */
    private void writeErrorResponse(HttpServletResponse response, int httpStatus, String message, int errorCode)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(httpStatus);
        ApiResponse<Object> result = ApiResponse.error(message, errorCode);
        response.getOutputStream().write(
                objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8)
        );
        response.getOutputStream().flush();
    }
}
