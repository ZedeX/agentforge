package com.agent.memory.api.impl;

import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.model.EmbeddingVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 嵌入向量客户端 Mock 实现（F12.D5: write-time vectorization, bge-large-zh 1024 dim）。
 *
 * <p>Mock 实现：基于文本 hashCode 作为随机种子，生成 1024 维伪向量并 L2 归一化。
 * 相同文本会得到相同向量（确定性），不同文本大概率得到不同向量。
 *
 * <p>仅在 {@code memory.embedding.http-enabled=false}（默认）时激活，
 * 测试环境与无网关依赖的开发场景使用。生产环境通过设置
 * {@code memory.embedding.http-enabled=true} 切换到 {@link EmbeddingClientImpl}。
 *
 * @see EmbeddingClient
 * @see EmbeddingClientImpl
 */
@Component
@ConditionalOnProperty(name = "memory.embedding.http-enabled", havingValue = "false", matchIfMissing = true)
public class MockEmbeddingClientImpl implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(MockEmbeddingClientImpl.class);

    @Override
    public EmbeddingVector embed(String text) {
        float[] values = generateVector(text);
        log.debug("Mock embed (vector) textLen={} dim={}",
                text == null ? 0 : text.length(), values.length);
        return new EmbeddingVector(values);
    }

    @Override
    public float[] embed(String text, String tenantId) {
        float[] values = generateVector(text);
        log.debug("Mock embed (raw) textLen={} tenantId={} dim={}",
                text == null ? 0 : text.length(), tenantId, values.length);
        return values;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String tenantId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(generateVector(text));
        }
        log.debug("Mock embedBatch size={} tenantId={} dim={}",
                texts.size(), tenantId, EmbeddingVector.DEFAULT_DIM);
        return result;
    }

    /**
     * 基于文本 hashCode 生成确定性 1024 维伪向量并 L2 归一化。
     */
    private float[] generateVector(String text) {
        int seed = text == null ? 0 : text.hashCode();
        Random random = new Random(seed);
        float[] values = new float[EmbeddingVector.DEFAULT_DIM];
        double sumSq = 0.0;
        for (int i = 0; i < values.length; i++) {
            float v = random.nextFloat() * 2f - 1f; // [-1, 1)
            values[i] = v;
            sumSq += (double) v * v;
        }
        // L2 归一化，避免零向量
        if (sumSq > 0.0) {
            double norm = Math.sqrt(sumSq);
            for (int i = 0; i < values.length; i++) {
                values[i] = (float) (values[i] / norm);
            }
        }
        return values;
    }
}
