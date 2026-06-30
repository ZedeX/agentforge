package com.agent.memory.api.impl;

import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.model.EmbeddingVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 嵌入向量客户端实现（F12.D5: write-time vectorization, bge-large-zh 1024 dim）。
 *
 * <p>Mock 实现：基于文本 hashCode 作为随机种子，生成 1024 维伪向量并 L2 归一化。
 * 相同文本会得到相同向量（确定性），不同文本大概率得到不同向量。
 * 真实实现应调用 bge-large-zh 模型 HTTP 接口。</p>
 *
 * @see EmbeddingClient
 */
@Component
public class EmbeddingClientImpl implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClientImpl.class);

    @Override
    public EmbeddingVector embed(String text) {
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
        log.debug("生成嵌入向量 textLen={} dim={}",
                text == null ? 0 : text.length(), values.length);
        return new EmbeddingVector(values);
    }
}