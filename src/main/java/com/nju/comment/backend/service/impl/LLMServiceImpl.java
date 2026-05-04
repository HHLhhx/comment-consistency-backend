package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.OpenAiModelFactory;
import com.nju.comment.backend.context.UserApiContext;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final OpenAiModelFactory openAiModelFactory;
    private final UserApiKeyService userApiKeyService;
    private final PromptService promptService;

    @Override
    public String generateComment(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        long startTime = System.currentTimeMillis();
        String requestId = request.getRequestId();

        try {
            // 调用前检查中断状态
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("线程已被中断");
            }

            ChatClient client = openAiModelFactory.getChatModelClient(request.getModelName());
            String systemPrompt = promptService.getSystemPrompt(request);
            String userPrompt = promptService.buildUserPrompt(request);

            log.info("调用LLM生成注释，requestId={}", requestId);

            // 执行LLM调用，期间可被线程中断
            String result = client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            // 调用后再次检查中断状态
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("线程已被中断");
            }

            long duration = System.currentTimeMillis() - startTime;
            log.debug("LLM生成注释完成，耗时：{}ms，requestId：{}，内容：\n{}", duration, requestId, result);
            return result;

        } catch (ResourceAccessException e) {
            // Spring AI 网络层包装异常（含 IOException / InterruptedException）
            if (isInterrupted(e)) {
                log.info("LLM生成注释在执行中被中断，耗时：{}ms，requestId：{}",
                        elapsed(startTime), requestId);
                Thread.currentThread().interrupt();
                throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", requestId);
            }
            log.error("LLM网络请求失败，requestId：{}", requestId, e);
            throw new LLMException(ErrorCode.LLM_CONNECTION_ERROR, "LLM连接失败", e);

        } catch (NonTransientAiException e) {
            // OpenAI 协议下不可重试错误（4xx 居多），按 HTTP 状态码区分
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401") || msg.contains("403")) {
                log.warn("401、403报错，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
                throw new LLMException(ErrorCode.LLM_API_KEY_INVALID,
                        "API Key 无效，请检查后重新配置: " + e.getMessage(), requestId);
            }
            if (msg.contains("404")) {
                log.warn("404报错，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
                throw new LLMException(ErrorCode.LLM_MODEL_NOT_FOUND,
                        e.getMessage(), requestId);
            }
            if (msg.contains("429")) {
                log.warn("LLM 服务限流，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
                throw new LLMException(ErrorCode.LLM_RATE_LIMIT,
                        "LLM 服务限流，请稍后重试", requestId);
            }
            log.error("LLM服务请求被拒绝，耗时：{}ms，requestId：{}", elapsed(startTime), requestId, e);
            throw new LLMException(ErrorCode.LLM_SERVICE_ERROR, "LLM服务请求失败: " + msg, e);

        } catch (TransientAiException e) {
            // OpenAI 协议下可重试错误（5xx / 网络抖动）经过 Spring AI retry 仍失败
            log.error("LLM 服务暂时不可用，耗时：{}ms，requestId：{}", elapsed(startTime), requestId, e);
            throw new LLMException(ErrorCode.LLM_UNAVAILABLE,
                    "LLM 服务暂时不可用，请稍后重试", e);

        } catch (InterruptedException e) {
            log.info("LLM生成注释被中断，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
            Thread.currentThread().interrupt();
            throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", requestId);

        } catch (ServiceException e) {
            // PromptService / OpenAiModelFactory 等内部组件抛出的业务异常，直接透传
            throw e;

        } catch (Exception e) {
            log.error("LLM生成注释失败，耗时：{}ms，requestId：{}", elapsed(startTime), requestId, e);
            throw new LLMException(ErrorCode.LLM_SERVICE_ERROR, "LLM服务异常", e);
        }
    }

    /**
     * 检查异常链中是否包含 InterruptedException（Spring AI包装异常，需要解包）
     */
    private boolean isInterrupted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public List<String> getAvailableModels(String username) {
        // 获取当前用户的 API Key 与 Base URL，并设入上下文
        String apiKey = userApiKeyService.getDecryptedApiKey(username);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException(ErrorCode.LLM_API_KEY_NOT_SET);
        }
        String baseUrl = userApiKeyService.getBaseUrl(username);
        UserApiContext.setCredential(apiKey, baseUrl);

        try {
            return openAiModelFactory.getAvailableChatModels();
        } catch (NonTransientAiException e) {
            // OpenAiModelFactory 内部已尽量映射成 LLMException，此处兜底
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401") || msg.contains("403")) {
                log.warn("获取模型列表失败：API Key 无效，username：{}", username);
                throw new ServiceException(ErrorCode.LLM_API_KEY_INVALID);
            }
            if (msg.contains("429")) {
                log.warn("获取模型列表失败：被限流，username：{}", username);
                throw new ServiceException(ErrorCode.LLM_RATE_LIMIT);
            }
            log.error("获取模型列表失败，username：{}", username, e);
            throw new ServiceException(ErrorCode.LLM_SERVICE_ERROR, "获取模型列表失败: " + msg);
        } finally {
            UserApiContext.clear();
        }
    }
}
