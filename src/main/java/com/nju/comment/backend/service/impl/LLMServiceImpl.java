package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.OllamaModelFactory;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final OllamaModelFactory ollamaModelFactory;
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

            ChatClient client = ollamaModelFactory.getChatModelClient(request.getModelName());
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
            // Spring AI 包装异常：ResourceAccessException -> IOException -> InterruptedException
            if (isInterrupted(e)) {
                log.info("LLM生成注释在执行中被中断，耗时：{}ms，requestId：{}",
                        elapsed(startTime), requestId);
                Thread.currentThread().interrupt();
                throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", requestId);
            }
            log.error("LLM网络请求失败，requestId：{}", requestId, e);
            throw new LLMException(ErrorCode.LLM_CONNECTION_ERROR, "LLM连接失败", e);

        } catch (NonTransientAiException e) {
            // Spring AI 不可重试异常，根据 HTTP 状态码区分具体原因
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401")) {
                log.warn("API Key 无效，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
                throw new LLMException(ErrorCode.LLM_API_KEY_INVALID,
                        "API Key 无效，请检查后重新配置", requestId);
            }
            if (msg.contains("404")) {
                log.warn("指定的LLM模型不存在，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
                throw new LLMException(ErrorCode.LLM_MODEL_NOT_FOUND,
                        "指定的模型不存在: " + request.getModelName(), requestId);
            }
            log.error("LLM服务请求被拒绝，耗时：{}ms，requestId：{}", elapsed(startTime), requestId, e);
            throw new LLMException(ErrorCode.LLM_SERVICE_ERROR, "LLM服务请求失败: " + msg, e);

        } catch (InterruptedException e) {
            log.info("LLM生成注释被中断，耗时：{}ms，requestId：{}", elapsed(startTime), requestId);
            Thread.currentThread().interrupt();
            throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", requestId);

        } catch (ServiceException e) {
            // PromptService 等内部组件抛出的业务异常，直接透传
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
        // 获取当前用户的 API Key 并设入上下文
        String apiKey = userApiKeyService.getDecryptedApiKey(username);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException(ErrorCode.LLM_API_KEY_NOT_SET);
        }
        UserApiContext.setApiKey(apiKey);

        try {
            return ollamaModelFactory.getAvailableChatModels();
        } catch (NonTransientAiException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401")) {
                log.warn("获取模型列表失败：API Key 无效，username：{}", username);
                throw new ServiceException(ErrorCode.LLM_API_KEY_INVALID);
            }
            log.error("获取模型列表失败，username：{}", username, e);
            throw new ServiceException(ErrorCode.LLM_SERVICE_ERROR, "获取模型列表失败: " + msg);
        } finally {
            UserApiContext.clear();
        }
    }
}
