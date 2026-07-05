package com.agent.knowledge.milvus;

import com.agent.knowledge.api.EmbeddingService;
import com.agent.knowledge.config.KnowledgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP-backed embedding service implementation (Plan 08 T10).
 *
 * <p>Active when {@code knowledge.milvus.enabled=true}. Calls an external
 * OpenAI-compatible embedding API (e.g., bge-large-zh) at
 * {@code knowledge.embedding.endpoint} via {@link WebClient}.</p>
 *
 * <p>Request format (OpenAI-compatible):</p>
 * <pre>{@code
 * POST /v1/embeddings
 * {
 *   "model": "bge-large-zh",
 *   "input": ["text1", "text2"]
 * }
 * }</pre>
 *
 * <p>Response format:</p>
 * <pre>{@code
 * {
 *   "data": [
 *     {"embedding": [0.1, 0.2, ...], "index": 0},
 *     {"embedding": [0.3, 0.4, ...], "index": 1}
 *   ],
 *   "model": "bge-large-zh",
 *   "usage": {"prompt_tokens": 10, "total_tokens": 10}
 * }
 * }</pre>
 *
 * @see EmbeddingService
 * @see com.agent.knowledge.api.impl.EmbeddingServiceImpl
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.milvus.enabled", havingValue = "true")
public class MilvusEmbeddingServiceImpl implements EmbeddingService {

    private static final String DEFAULT_MODEL = "bge-large-zh";
    private static final int DEFAULT_DIMENSION = 1024;

    private final WebClient webClient;
    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MilvusEmbeddingServiceImpl(KnowledgeProperties properties) {
        this.properties = properties;
        String endpoint = properties.getEmbedding().getEndpoint();
        this.webClient = WebClient.builder().baseUrl(endpoint).build();
        log.info("MilvusEmbeddingServiceImpl initialized with endpoint: {}", endpoint);
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[DEFAULT_DIMENSION];
        }
        try {
            ObjectNode requestBody = buildRequestBody(List.of(text));
            JsonNode response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return parseFirstVector(response);
        } catch (Exception e) {
            log.warn("HTTP embedding call failed, returning zero vector: {}", e.getMessage());
            return new float[DEFAULT_DIMENSION];
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ObjectNode requestBody = buildRequestBody(texts);
            JsonNode response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return parseAllVectors(response, texts.size());
        } catch (Exception e) {
            log.warn("HTTP batch embedding call failed, returning zero vectors: {}", e.getMessage());
            List<float[]> fallback = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                fallback.add(new float[DEFAULT_DIMENSION]);
            }
            return fallback;
        }
    }

    @Override
    public int getDimension() {
        return DEFAULT_DIMENSION;
    }

    private ObjectNode buildRequestBody(List<String> texts) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", DEFAULT_MODEL);
        ArrayNode inputArray = body.putArray("input");
        for (String text : texts) {
            inputArray.add(text);
        }
        return body;
    }

    private float[] parseFirstVector(JsonNode response) {
        if (response == null || !response.has("data")) {
            return new float[DEFAULT_DIMENSION];
        }
        JsonNode firstEmbedding = response.get("data").get(0).get("embedding");
        return toFloatArray(firstEmbedding);
    }

    private List<float[]> parseAllVectors(JsonNode response, int expectedSize) {
        List<float[]> result = new ArrayList<>(expectedSize);
        if (response == null || !response.has("data")) {
            for (int i = 0; i < expectedSize; i++) {
                result.add(new float[DEFAULT_DIMENSION]);
            }
            return result;
        }
        JsonNode dataArray = response.get("data");
        for (int i = 0; i < expectedSize; i++) {
            JsonNode item = dataArray.get(i);
            if (item != null && item.has("embedding")) {
                result.add(toFloatArray(item.get("embedding")));
            } else {
                result.add(new float[DEFAULT_DIMENSION]);
            }
        }
        return result;
    }

    private static float[] toFloatArray(JsonNode embeddingNode) {
        if (embeddingNode == null || !embeddingNode.isArray()) {
            return new float[DEFAULT_DIMENSION];
        }
        int size = embeddingNode.size();
        float[] vector = new float[size];
        for (int i = 0; i < size; i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }
}
