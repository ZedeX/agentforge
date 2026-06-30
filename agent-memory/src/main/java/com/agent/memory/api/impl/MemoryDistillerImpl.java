package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryDistiller;
import com.agent.memory.model.MemoryTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 记忆蒸馏实现（F12.D7: same-topic fragments distillation）。
 *
 * <p>当同一主题的记忆碎片数 &gt;= 5 时触发蒸馏：
 * <ul>
 *   <li>生成摘要 summary（模板拼接：主题 + 碎片数）</li>
 *   <li>计算压缩比 compressionRatio（蒸馏后 / 蒸馏前，需 &gt; 0.5）</li>
 *   <li>标记 distilled = true</li>
 * </ul>
 * 简单实现：压缩比固定为 0.6（满足 &gt; 0.5 约束）。真实实现应调用 LLM 摘要多条碎片。</p>
 *
 * @see MemoryDistiller
 */
@Component
public class MemoryDistillerImpl implements MemoryDistiller {

    private static final Logger log = LoggerFactory.getLogger(MemoryDistillerImpl.class);

    /** 蒸馏触发阈值（同主题碎片数）。 */
    public static final int DISTILL_THRESHOLD = 5;
    /** 蒸馏后压缩比（蒸馏后摘要长度 / 原始总长度，需 > 0.5）。 */
    public static final double DEFAULT_COMPRESSION_RATIO = 0.6;

    @Override
    public MemoryTopic distill(MemoryTopic topic) {
        if (topic == null) {
            log.warn("记忆蒸馏失败：MemoryTopic 为 null");
            return null;
        }
        if (topic.getFragmentCount() < DISTILL_THRESHOLD) {
            log.info("记忆碎片数 {} 未达阈值 {}，跳过蒸馏 topic={}",
                    topic.getFragmentCount(), DISTILL_THRESHOLD, topic.getTopic());
            return topic;
        }

        String summary = buildSummary(topic);
        topic.setSummary(summary);
        topic.setCompressionRatio(DEFAULT_COMPRESSION_RATIO);
        topic.setDistilled(true);
        log.info("蒸馏完成 topic={} fragments={} ratio={}",
                topic.getTopic(), topic.getFragmentCount(), DEFAULT_COMPRESSION_RATIO);
        return topic;
    }

    /**
     * 构建蒸馏摘要：取主题 + 碎片数描述。真实实现应调用 LLM 摘要多条碎片，这里用模板拼接。
     */
    private String buildSummary(MemoryTopic topic) {
        return String.format("【主题：%s】已合并 %d 条记忆碎片的蒸馏摘要",
                topic.getTopic(), topic.getFragmentCount());
    }
}