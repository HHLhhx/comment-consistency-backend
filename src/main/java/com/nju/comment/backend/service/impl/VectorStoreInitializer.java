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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreInitializer {

    private final VectorStore vectorStore;

    private final ObjectMapper objectMapper;

    @Value("classpath:/docs/test.jsonl")
    private Resource[] resources;

    @Value("${app.ai.ollama.embedding.enable:false}")
    private boolean enable;

    @PostConstruct
    public void init() {
        if (!enable) {
            log.info("VectorStore initialization is disabled.");
            return;
        }

        log.info("Initializing VectorStore...");
        for (Resource resource : resources) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                List<Document> docs = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode jsonNode = new ObjectMapper().readTree(line);
                    ObjectNode newJson = objectMapper.createObjectNode();
                    if (jsonNode.has("src_method") && jsonNode.has("dst_method")
                            && jsonNode.has("src_javadoc") && jsonNode.has("dst_javadoc")) {
                        newJson.put("src_method", jsonNode.get("src_method").asText());
                        newJson.put("dst_method", jsonNode.get("dst_method").asText());
                        newJson.put("src_javadoc", jsonNode.get("src_javadoc").asText());
                        newJson.put("dst_javadoc", jsonNode.get("dst_javadoc").asText());
                    }
                    Document doc = new Document(objectMapper.writeValueAsString(newJson));
                    docs.add(doc);
                }
                System.out.println("size: " + docs.size());
                System.out.println("Loading " + docs.size() + " documents from " + resource.getFilename());
                vectorStore.add(docs);
                System.out.println("add complete");
            } catch (IOException e) {
                log.error("向量数据库初始化失败", e);
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INIT_ERROR, "向量数据库初始化失败", e);
            }
        }
        log.info("VectorStore init complete.");
    }
}
