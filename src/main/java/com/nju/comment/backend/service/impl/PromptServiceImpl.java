package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.service.PromptService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final Configuration freemarkerConfiguration;

    @Value("${app.prompt.template-name:comment_template.ftl}")
    private String templateName;

    @Override
    public String buildPrompt(CommentRequest request) {
        log.debug("构建提示词开始");

        Map<String, Object> context = new HashMap<>();
        context.put("code", request.getCode());
        context.put("existingComment", request.getExistingComment());
        context.put("language", request.getLanguage());
        context.put("includeParams", request.getOptions().isIncludeParams());
        context.put("includeReturn", request.getOptions().isIncludeReturn());
        context.put("includeExceptions", request.getOptions().isIncludeExceptions());
        context.put("style", request.getOptions().getStyle());
        context.put("commentLanguage", request.getOptions().getLanguage());

        if (request.getContext() != null) {
            context.put("className", request.getContext().getClassName());
            context.put("packageName", request.getContext().getPackageName());

            if (request.getContext().getRelatedMethods() != null) {
                List<Map<String, Object>> methodsForTemplate = request.getContext().getRelatedMethods().stream()
                        .map(m -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("name", m.getName());
                            map.put("signature", m.getSignature());
                            map.put("comment", m.getComment());
                            return map;
                        })
                        .toList();
                context.put("relatedMethods", methodsForTemplate);
            } else {
                context.put("relatedMethods", null);
            }
        } else {
            context.put("className", null);
            context.put("packageName", null);
            context.put("relatedMethods", null);
        }

        try {
            Template template = freemarkerConfiguration.getTemplate(templateName);

            StringWriter writer = new StringWriter();
            template.process(context, writer);

            String prompt = writer.toString();
            log.debug("构建提示词完成，内容：{}", prompt);
            return prompt;
        } catch (Exception e) {
            log.error("构建提示词失败", e);
            throw new RuntimeException("构建提示词失败: " + e.getMessage(), e);
        }
    }
}
