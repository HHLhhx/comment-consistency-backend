package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.OllamaModelFactory;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final OllamaModelFactory ollamaModelFactory;

    private final PromptService promptService;

    @Override
    public String generateComment(CommentRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        }

        long startTime = System.currentTimeMillis();

        ChatClient client = ollamaModelFactory.getChatModelClient(request.getModelName());
        try {
            // 检查线程中断状态
            if (Thread.currentThread().isInterrupted()) {
                log.info("LLM调用前检测到线程中断，requestId={}", request.getRequestId());
                throw new InterruptedException("线程已被中断");
            }

            log.info("调用LLM生成注释");

            try {
                String systemPrompt = promptService.getSystemPrompt(request);
                String userPrompt = promptService.buildUserPrompt(request);

                // 执行LLM调用，期间可被线程中断
                String result = client.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                // 再次检查中断状态
                if (Thread.currentThread().isInterrupted()) {
                    log.info("LLM调用后检测到线程中断，requestId={}", request.getRequestId());
                    throw new InterruptedException("线程已被中断");
                }

                long duration = System.currentTimeMillis() - startTime;
                log.debug("LLM生成注释完成，耗时：{}ms，requestId：{}，内容：\n{}",
                        duration, request.getRequestId(), result);
                return result;
            } catch (ResourceAccessException e) {
                // Spring AI 包装异常：ResourceAccessException -> IOException -> InterruptedException
                if (isInterrupted(e)) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("LLM生成注释在执行中被中断，耗时：{}ms，requestId：{}",
                            duration, request.getRequestId());
                    Thread.currentThread().interrupt();
                    throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", request.getRequestId());
                }
                log.error("LLM网络请求失败，requestId：{}", request.getRequestId(), e);
                throw new LLMException(ErrorCode.LLM_CONNECTION_ERROR, "LLM连接失败", e);
            } catch (InterruptedException e) {
                // 线程在LLM调用期间被直接中断（在调用前）
                long duration = System.currentTimeMillis() - startTime;
                log.info("线程在LLM调用期间被直接中断，耗时：{}ms，requestId：{}",
                        duration, request.getRequestId());
                Thread.currentThread().interrupt();
                throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", request.getRequestId());
            }
        } catch (ServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("LLM生成注释被中断，耗时：{}ms，requestId：{}", duration, request.getRequestId());
            Thread.currentThread().interrupt();
            throw new LLMException(ErrorCode.LLM_INTERRUPTED, "请求已取消", request.getRequestId());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("LLM生成注释失败，耗时：{}ms，requestId：{}", duration, request.getRequestId(), e);
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

    @Override
    public List<String> getAvailableModels() {
        return ollamaModelFactory.getAvailableChatModels();
    }
}
