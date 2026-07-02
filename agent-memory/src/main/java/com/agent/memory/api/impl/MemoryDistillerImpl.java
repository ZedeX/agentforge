package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryDistiller;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.distiller.DistillPromptBuilder;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.gateway.ModelGatewayClient;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemoryTopic;
import com.agent.memory.repository.MemoryRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 记忆蒸馏实现（Plan 03 T4 + F12.D7 backward-compatible）.
 *
 * <p>两条路径共存：
 * <ul>
 *   <li>{@code distill(tenantId, topic, activeRecords)} — Plan 03 T4 设计：调用
 *       {@link ModelGatewayClient} 生成摘要，创建 DISTILLED 记录，归档源 ACTIVE 记录，
 *       持久化到 repository。模型失败时不归档源记录（保留 ACTIVE）。</li>
 *   <li>{@code distill(MemoryTopic)} — F12 骨架向后兼容：模板拼接，不调模型，不持久化。</li>
 * </ul>
 *
 * <p>Importance level 阈值（doc 04-memory §4.3）：score≥0.7 → HIGH / 0.4≤score&lt;0.7 → MEDIUM / score&lt;0.4 → LOW。
 *
 * @see MemoryDistiller
 * @see DistillPromptBuilder
 */
@Component
public class MemoryDistillerImpl implements MemoryDistiller {

    private static final Logger log = LoggerFactory.getLogger(MemoryDistillerImpl.class);

    /** 旧接口蒸馏触发阈值（同主题碎片数，F12.D7 设计）。 */
    public static final int DISTILL_THRESHOLD = 5;
    /** 旧接口蒸馏后压缩比（蒸馏后摘要长度 / 原始总长度，需 > 0.5）。 */
    public static final double DEFAULT_COMPRESSION_RATIO = 0.6;

    /** HIGH 重要性阈值（doc 04 §4.3）。 */
    private static final double HIGH_THRESHOLD = 0.7;
    /** LOW 重要性阈值（doc 04 §4.3）。 */
    private static final double LOW_THRESHOLD = 0.4;

    private final ModelGatewayClient modelGatewayClient;
    private final MemoryRecordRepository repository;
    private final MemoryProperties properties;

    public MemoryDistillerImpl(ModelGatewayClient modelGatewayClient,
                               MemoryRecordRepository repository,
                               MemoryProperties properties) {
        this.modelGatewayClient = modelGatewayClient;
        this.repository = repository;
        this.properties = properties;
    }

    // ============ Plan 03 T4 新接口 ============

    @Override
    public MemoryRecord distill(String tenantId, String topic, List<MemoryRecord> activeRecords) {
        if (activeRecords == null || activeRecords.isEmpty()) {
            log.debug("蒸馏跳过：activeRecords 为 null 或空 tenantId={} topic={}", tenantId, topic);
            return null;
        }

        int triggerCount = properties.getDistill().getTriggerCount();
        if (activeRecords.size() < triggerCount) {
            log.info("蒸馏跳过：ACTIVE 记忆数 {} 未达阈值 {} tenantId={} topic={}",
                    activeRecords.size(), triggerCount, tenantId, topic);
            return null;
        }

        // 1. 构造 prompt
        String systemPrompt = DistillPromptBuilder.buildSystemPrompt(activeRecords.size());
        String userPrompt = DistillPromptBuilder.buildUserPrompt(activeRecords);

        // 2. 调模型生成摘要（失败抛异常，源记录不归档）
        String summary;
        try {
            summary = modelGatewayClient.chat(systemPrompt, userPrompt);
        } catch (RuntimeException ex) {
            log.error("蒸馏失败：模型调用异常 tenantId={} topic={} size={} error={}",
                    tenantId, topic, activeRecords.size(), ex.getMessage());
            throw ex;  // 保留源 ACTIVE 状态
        }

        if (summary == null || summary.isBlank()) {
            log.warn("蒸馏失败：模型返回空摘要 tenantId={} topic={}", tenantId, topic);
            throw new RuntimeException("model gateway returned empty summary");
        }

        // 3. 计算聚合 importance
        double aggregatedImportance = aggregateImportance(activeRecords);
        String importanceLevel = classifyImportance(aggregatedImportance);

        // 4. 创建 DISTILLED 记录
        MemoryRecord distilled = new MemoryRecord();
        distilled.setMemoryId(generateMemoryId());
        distilled.setTenantId(tenantId);
        distilled.setType(activeRecords.get(0).getType());  // 沿用主导类型
        distilled.setStatus(MemoryStatus.DISTILLED);
        distilled.setContent(summary);
        distilled.setTopic(topic);
        distilled.setImportanceScore(aggregatedImportance);
        distilled.setImportanceLevel(importanceLevel);
        distilled.setDistillCount(1);

        // 5. 归档源记录并持久化
        for (MemoryRecord source : activeRecords) {
            source.setStatus(MemoryStatus.ARCHIVED);
            repository.save(source);
        }

        // 6. 持久化 distilled 记录
        repository.save(distilled);

        log.info("蒸馏完成 tenantId={} topic={} sourceCount={} aggregatedImportance={} level={} summaryLen={}",
                tenantId, topic, activeRecords.size(), aggregatedImportance, importanceLevel, summary.length());
        return distilled;
    }

    // ============ F12.D7 旧接口（向后兼容） ============

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

        String summary = buildTemplateSummary(topic);
        topic.setSummary(summary);
        topic.setCompressionRatio(DEFAULT_COMPRESSION_RATIO);
        topic.setDistilled(true);
        log.info("蒸馏完成（旧接口） topic={} fragments={} ratio={}",
                topic.getTopic(), topic.getFragmentCount(), DEFAULT_COMPRESSION_RATIO);
        return topic;
    }

    // ============ 私有工具方法 ============

    /** 计算聚合 importance（取所有源记录 importance 的平均值）。 */
    private double aggregateImportance(List<MemoryRecord> records) {
        if (records.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (MemoryRecord r : records) {
            sum += r.getImportanceScore();
        }
        return sum / records.size();
    }

    /** 按 doc 04 §4.3 阈值分级 importance。 */
    private String classifyImportance(double score) {
        if (score >= HIGH_THRESHOLD) {
            return "HIGH";
        } else if (score < LOW_THRESHOLD) {
            return "LOW";
        } else {
            return "MEDIUM";
        }
    }

    /** 生成 32 位 UUID（去掉横线，对齐 memory_id 列长度）。 */
    private String generateMemoryId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 旧接口模板摘要（不调模型）。 */
    private String buildTemplateSummary(MemoryTopic topic) {
        return String.format("【主题：%s】已合并 %d 条记忆碎片的蒸馏摘要",
                topic.getTopic(), topic.getFragmentCount());
    }
}
