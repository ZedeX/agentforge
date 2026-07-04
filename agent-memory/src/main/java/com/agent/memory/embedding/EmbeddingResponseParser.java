package com.agent.memory.embedding;

import com.agent.memory.exception.EmbeddingServiceFailureException;
import com.agent.memory.model.EmbeddingVector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * T5: 嵌入响应解析器（OpenAI 兼容格式）。
 *
 * <p>解析响应：
 * <pre>{@code
 * {
 *   "data": [
 *     {"embedding": [0.1, 0.2, ...], "index": 0},
 *     {"embedding": [0.3, 0.4, ...], "index": 1}
 *   ]
 * }
 * }</pre>
 *
 * <p>抽取 {@code data[*].embedding} 转换为 {@code List<float[]>}，
 * 维度校验（必须 1024）由调用方负责。
 */
public class EmbeddingResponseParser {

    private final ObjectMapper objectMapper;

    public EmbeddingResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public EmbeddingResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析响应体。
     *
     * @param responseBody HTTP 响应体 JSON 字符串
     * @return 向量列表，顺序与请求 input 一致
     * @throws EmbeddingServiceFailureException 当响应格式非法、data 缺失或为空时
     */
    public List<float[]> parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) {
                throw new EmbeddingServiceFailureException(
                        "Embedding response missing 'data' array: " + truncate(responseBody));
            }
            List<float[]> result = new ArrayList<>(dataNode.size());
            for (JsonNode item : dataNode) {
                JsonNode embNode = item.get("embedding");
                if (embNode == null || !embNode.isArray()) {
                    throw new EmbeddingServiceFailureException(
                            "Embedding response item missing 'embedding' array");
                }
                float[] vector = new float[embNode.size()];
                for (int i = 0; i < embNode.size(); i++) {
                    vector[i] = (float) embNode.get(i).asDouble();
                }
                result.add(vector);
            }
            return result;
        } catch (EmbeddingServiceFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingServiceFailureException(
                    "Failed to parse embedding response: " + truncate(responseBody), e);
        }
    }

    /**
     * 校验向量维度是否为 1024。
     */
    public static void validateDimension(float[] vector) {
        if (vector == null || vector.length != EmbeddingVector.DEFAULT_DIM) {
            throw new EmbeddingServiceFailureException(
                    "Embedding dimension mismatch: expected=" + EmbeddingVector.DEFAULT_DIM
                            + ", actual=" + (vector == null ? 0 : vector.length));
        }
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
