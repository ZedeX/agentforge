package com.agent.memory.embedding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T5: 嵌入请求体构造器（OpenAI 兼容格式）。
 *
 * <p>构造请求体：
 * <pre>{@code
 * {
 *   "model": "text-embedding-v3",
 *   "input": ["text1", "text2", ...]
 * }
 * }</pre>
 *
 * <p>对齐 doc 04-memory §7.2: 模型默认 text-embedding-v3，1024 维。
 * WebClient 通过 Jackson 自动序列化为 JSON。
 */
public class EmbeddingRequestBuilder {

    private final String model;

    public EmbeddingRequestBuilder(String model) {
        this.model = model;
    }

    /**
     * 构造嵌入请求体。
     *
     * @param texts 待嵌入文本列表（顺序与返回向量顺序一致）
     * @return 请求体 Map（被 WebClient 序列化为 JSON）
     */
    public Map<String, Object> build(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("model", model);
        body.put("input", texts);
        return body;
    }
}
