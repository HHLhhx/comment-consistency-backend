package com.nju.comment.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.VectorStoreException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreInitializer {

    private static final String DOCS_RESOURCE_PATTERN = "classpath*:/docs/**/*.jsonl";
    private static final int PROGRESS_BAR_WIDTH = 40;
    private static final int PROGRESS_STEP = 50;

    private final VectorStore vectorStore;

    private final ObjectMapper objectMapper;

    private final ResourcePatternResolver resourcePatternResolver;

    @Value("${app.vectorstore.init:false}")
    private boolean enable;

    @Value("${app.vectorstore.embedding-batch-size:32}")
    private int embeddingBatchSize;

    @Value("${app.vectorstore.embedding-max-input-chars:7000}")
    private int embeddingMaxInputChars;

    @Value("${app.vectorstore.progress-file:logs/vectorstore-init-progress.properties}")
    private String progressFile;

    @Value("${app.vectorstore.clear-progress-on-success:true}")
    private boolean clearProgressOnSuccess;

    @PostConstruct
    public void init() {
        if (!enable) {
            log.info("VectorStore initialization is disabled.");
            return;
        }

        Resource[] resources = loadResources();
        if (resources.length == 0) {
            log.warn("No documents found in {}, skip initialization.", DOCS_RESOURCE_PATTERN);
            return;
        }

        log.info("Initializing VectorStore...");
        int[] fileTotals = countLinesPerResource(resources);
        int total = Arrays.stream(fileTotals).sum();
        if (total <= 0) {
            log.warn("No valid lines found in {}, skip initialization.", DOCS_RESOURCE_PATTERN);
            return;
        }

        int globalProcessed = 0;
        int globalSkipped = 0;
        int globalInvalidSkipped = 0;
        int globalTooLongSkipped = 0;
        int batchSize = Math.max(1, embeddingBatchSize);
        Set<String> seenDocIds = new HashSet<>(Math.max(total, 1024));
        Properties progressProperties = loadProgressProperties();

        for (int i = 0; i < resources.length; i++) {
            Resource resource = resources[i];
            int fileTotal = fileTotals[i];
            int fileProcessed = 0;
            int nextProgressMark = 1;
            String progressKey = buildProgressKey(resource);
            int resumeLine = parsePositiveInt(progressProperties.getProperty(progressKey), 0);

            if (resumeLine > 0) {
                log.info("检测到断点续跑位置: file={}, resumeLine={}", resolveResourceName(resource), resumeLine);
            }

            if (fileTotal == 0) {
                log.info("Skip empty file: {}", resolveResourceName(resource));
                continue;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<Document> docs = new ArrayList<>(batchSize);
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (lineNumber <= resumeLine) {
                        continue;
                    }

                    if (lineNumber % PROGRESS_STEP == 0) {
                        updateProgress(progressProperties, progressKey, lineNumber);
                    }

                    JsonNode jsonNode = objectMapper.readTree(line);
                    String srcMethod = jsonNode.path("src_method").asText("");
                    String dstMethod = jsonNode.path("dst_method").asText("");
                    String srcJavadoc = jsonNode.path("src_javadoc").asText("");
                    String dstJavadoc = jsonNode.path("dst_javadoc").asText("");

                    ObjectNode keyJson = objectMapper.createObjectNode();
                    keyJson.put("src_method", srcMethod);
                    keyJson.put("dst_method", dstMethod);
                    keyJson.put("src_javadoc", srcJavadoc);

                    ObjectNode vectorTextJson = objectMapper.createObjectNode();
                    vectorTextJson.put("src_method", srcMethod);
                    vectorTextJson.put("dst_method", dstMethod);
                    vectorTextJson.put("src_javadoc", srcJavadoc);

                    String vectorText = objectMapper.writeValueAsString(vectorTextJson);
                    if (isOverlongForEmbedding(vectorText)) {
                        globalTooLongSkipped++;
                        globalInvalidSkipped++;
                        log.warn("跳过过长向量样本: file={}, lineNumber={}, contentLength={}, maxAllowed={}",
                                resolveResourceName(resource), lineNumber, vectorText.length(), Math.max(256, embeddingMaxInputChars));
                        continue;
                    }

                    String docId = sha256Hex(objectMapper.writeValueAsString(keyJson)).substring(0, 32);
                    if (!seenDocIds.add(docId)) {
                        globalSkipped++;
                        continue;
                    }

                    Document doc = new Document(
                            docId,
                            vectorText,
                            Map.of(
                                    "src_method", srcMethod,
                                    "dst_method", dstMethod,
                                    "src_javadoc", srcJavadoc,
                                    "dst_javadoc", dstJavadoc));
                    docs.add(doc);

                    fileProcessed++;
                    globalProcessed++;
                    if (fileProcessed >= nextProgressMark || fileProcessed == fileTotal) {
                        System.out.print("\r" + buildFileProgressLine(
                                i + 1,
                                resources.length,
                                resolveResourceName(resource),
                                fileProcessed,
                                fileTotal,
                                globalProcessed,
                                total) + "\r");
                        nextProgressMark = fileProcessed + PROGRESS_STEP;
                    }

                    if (docs.size() >= batchSize) {
                        try {
                            globalInvalidSkipped += addDocumentsWithFallback(
                                    docs,
                                    resolveResourceName(resource),
                                    fileProcessed,
                                    globalProcessed
                            );
                                updateProgress(progressProperties, progressKey, lineNumber);
                        } catch (Exception e) {
                            log.error("Batch insert failed at file={}, fileProcessed={}, globalProcessed={}, batchSize={}",
                                    resolveResourceName(resource), fileProcessed, globalProcessed, docs.size(), e);
                            throw e;
                        }
                        docs.clear();
                    }
                }

                if (!docs.isEmpty()) {
                    try {
                        globalInvalidSkipped += addDocumentsWithFallback(
                                docs,
                                resolveResourceName(resource),
                                fileProcessed,
                                globalProcessed
                        );
                        updateProgress(progressProperties, progressKey, lineNumber);
                    } catch (Exception e) {
                        log.error("Final batch insert failed at file={}, fileProcessed={}, globalProcessed={}, batchSize={}",
                                resolveResourceName(resource), fileProcessed, globalProcessed, docs.size(), e);
                        throw e;
                    }
                }

                // 文件读取完成后写入最新断点
                updateProgress(progressProperties, progressKey, fileTotal);
                System.out.println();
            } catch (Exception e) {
                log.error("向量数据库初始化失败", e);
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INIT_ERROR, "向量数据库初始化失败", e);
            }
        }

        if (clearProgressOnSuccess) {
            clearProgressFile();
        }

        log.info("VectorStore init complete. {} documents processed, {} duplicates skipped, {} invalid documents skipped, {} overlong skipped.",
                globalProcessed, globalSkipped, globalInvalidSkipped, globalTooLongSkipped);
    }

    private int addDocumentsWithFallback(List<Document> docs,
                                         String fileName,
                                         int fileProcessed,
                                         int globalProcessed) {
        if (docs == null || docs.isEmpty()) {
            return 0;
        }

        try {
            vectorStore.add(docs);
            return 0;
        } catch (Exception e) {
            if (!isBadRequest(e)) {
                throw e;
            }

            if (docs.size() > 1) {
                int middle = docs.size() / 2;
                List<Document> left = new ArrayList<>(docs.subList(0, middle));
                List<Document> right = new ArrayList<>(docs.subList(middle, docs.size()));
                log.warn("Embedding batch 400，执行二分降级重试: file={}, fileProcessed={}, globalProcessed={}, batchSize={}",
                        fileName, fileProcessed, globalProcessed, docs.size());
                int skippedLeft = addDocumentsWithFallback(left, fileName, fileProcessed, globalProcessed);
                int skippedRight = addDocumentsWithFallback(right, fileName, fileProcessed, globalProcessed);
                return skippedLeft + skippedRight;
            }

            Document single = docs.get(0);
            String docId = single.getId();
            int contentLength = single.getText() == null ? 0 : single.getText().length();
            log.error("跳过不可嵌入文档: file={}, fileProcessed={}, globalProcessed={}, docId={}, contentLength={}",
                    fileName, fileProcessed, globalProcessed, docId, contentLength, e);
            return 1;
        }
    }

    private boolean isOverlongForEmbedding(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return false;
        }

        int maxChars = Math.max(256, embeddingMaxInputChars);
        int totalCodePoints = rawText.codePointCount(0, rawText.length());
        return totalCodePoints > maxChars;
    }

    private boolean isBadRequest(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String normalized = msg.toLowerCase();
                if (normalized.contains(" 400 ")
                        || normalized.contains("400 -")
                        || normalized.contains("400 bad request")
                        || normalized.contains("http 400")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private Resource[] loadResources() {
        try {
            return resourcePatternResolver.getResources(DOCS_RESOURCE_PATTERN);
        } catch (IOException e) {
            log.warn("Failed to resolve documents with pattern {}, skip initialization.", DOCS_RESOURCE_PATTERN, e);
            return new Resource[0];
        }
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private int[] countLinesPerResource(Resource[] resourceList) {
        int[] lines = new int[resourceList.length];
        for (int i = 0; i < resourceList.length; i++) {
            Resource resource = resourceList[i];
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    lines[i]++;
                }
            } catch (IOException e) {
                log.error("向量数据库初始化失败", e);
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INIT_ERROR, "向量数据库初始化失败", e);
            }
        }
        return lines;
    }

    private String buildProgressBar(int current, int total, int width) {
        int safeTotal = Math.max(total, 1);
        int percent = (int) ((current * 100L) / safeTotal);
        int filled = (int) (((long) current * width) / safeTotal);

        StringBuilder sb = new StringBuilder(width + 32);
        sb.append('[');
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? '=' : ' ');
        }
        sb.append("] ");
        if (percent < 10) {
            sb.append("  ");
        } else if (percent < 100) {
            sb.append(' ');
        }
        sb.append(percent).append("% (").append(current).append('/').append(total).append(')');
        return sb.toString();
    }

    private String buildFileProgressLine(int fileIndex,
                                         int fileCount,
                                         String fileName,
                                         int fileCurrent,
                                         int fileTotal,
                                         int globalCurrent,
                                         int globalTotal) {
        return "[" + fileIndex + "/" + fileCount + "] "
                + fileName + " "
                + buildProgressBar(fileCurrent, fileTotal, PROGRESS_BAR_WIDTH)
                + " | total "
                + buildProgressBar(globalCurrent, globalTotal, PROGRESS_BAR_WIDTH);
    }

    private String resolveResourceName(Resource resource) {
        String fileName = resource.getFilename();
        return fileName != null ? fileName : resource.getDescription();
    }

    private String buildProgressKey(Resource resource) {
        return resolveResourceName(resource);
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Properties loadProgressProperties() {
        Properties properties = new Properties();
        Path path = resolveProgressFilePath();
        if (!Files.exists(path)) {
            return properties;
        }

        try (var inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException e) {
            log.warn("读取向量初始化断点文件失败，忽略断点续跑: file={}", path, e);
        }
        return properties;
    }

    private void updateProgress(Properties properties, String key, int lineNumber) {
        properties.setProperty(key, String.valueOf(Math.max(0, lineNumber)));
        persistProgressProperties(properties);
    }

    private void persistProgressProperties(Properties properties) {
        Path path = resolveProgressFilePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "VectorStore initializer progress");
            }
        } catch (IOException e) {
            log.warn("写入向量初始化断点文件失败: file={}", path, e);
        }
    }

    private void clearProgressFile() {
        Path path = resolveProgressFilePath();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理向量初始化断点文件失败: file={}", path, e);
        }
    }

    private Path resolveProgressFilePath() {
        Path path = Paths.get(progressFile);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get("").toAbsolutePath().resolve(path).normalize();
    }
}
