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
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreInitializer {

    private static final int BATCH_SIZE = 256;
    private static final int PROGRESS_BAR_WIDTH = 40;
    private static final int PROGRESS_STEP = 50;

    private final VectorStore vectorStore;

    private final ObjectMapper objectMapper;

    @Value("classpath:/docs/data_test_clean_serialized.jsonl")
    private Resource[] resources;

    @Value("${app.vectorstore.init:false}")
    private boolean enable;

    @PostConstruct
    public void init() {
        if (!enable) {
            log.info("VectorStore initialization is disabled.");
            return;
        }

        log.info("Initializing VectorStore...");
        int[] fileTotals = countLinesPerResource(resources);
        int total = Arrays.stream(fileTotals).sum();
        if (total == 0) {
            log.warn("No documents found in classpath:/docs/**, skip initialization.");
            return;
        }

        int globalProcessed = 0;
        int globalSkipped = 0;
        Set<String> seenDocIds = new HashSet<>(Math.max(total, 1024));
        for (int i = 0; i < resources.length; i++) {
            Resource resource = resources[i];
            int fileTotal = fileTotals[i];
            int fileProcessed = 0;
            int nextProgressMark = 1;

            if (fileTotal == 0) {
                log.info("Skip empty file: {}", resolveResourceName(resource));
                continue;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<Document> docs = new ArrayList<>(BATCH_SIZE);
                String line;
                while ((line = reader.readLine()) != null) {
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

                    String docId = sha256Hex(objectMapper.writeValueAsString(keyJson)).substring(0, 32);
                    if (!seenDocIds.add(docId)) {
                        globalSkipped++;
                        continue;
                    }

                    Document doc = new Document(
                            docId,
                            objectMapper.writeValueAsString(vectorTextJson),
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

                    if (docs.size() >= BATCH_SIZE) {
                        try {
                            vectorStore.add(docs);
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
                        vectorStore.add(docs);
                    } catch (Exception e) {
                        log.error("Final batch insert failed at file={}, fileProcessed={}, globalProcessed={}, batchSize={}",
                                resolveResourceName(resource), fileProcessed, globalProcessed, docs.size(), e);
                        throw e;
                    }
                }
                System.out.println();
            } catch (IOException e) {
                log.error("向量数据库初始化失败", e);
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INIT_ERROR, "向量数据库初始化失败", e);
            } catch (Exception e) {
                log.error("向量数据库初始化失败", e);
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INIT_ERROR, "向量数据库初始化失败", e);
            }
        }
        log.info("VectorStore init complete. {} documents processed, {} duplicates skipped.", globalProcessed, globalSkipped);
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
        int filled = (int) ((current * 1L * width) / safeTotal);

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
}
