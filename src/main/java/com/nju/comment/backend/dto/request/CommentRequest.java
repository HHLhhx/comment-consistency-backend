package com.nju.comment.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {

    @NotBlank(message = "代码不能为空")
    private String code;

    private String existingComment;

    @NotBlank(message = "语言不能为空")
    private String language;

    private Context context;

    @Builder.Default
    private GenerationOptions options = GenerationOptions.defaultOptions();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Context {
        private String className;
        private String packageName;
        private List<MethodInfo> relatedMethods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationOptions {
        @Builder.Default
        private boolean includeParams = true;

        @Builder.Default
        private boolean includeReturn = true;

        @Builder.Default
        private boolean includeExceptions = false;

        @Builder.Default
        private String style = "Javadoc";

        @Builder.Default
        private String language = "Chinese";

        public static GenerationOptions defaultOptions() {
            return GenerationOptions.builder().build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodInfo {
        private String name;
        private String signature;
        private String comment;
    }
}
