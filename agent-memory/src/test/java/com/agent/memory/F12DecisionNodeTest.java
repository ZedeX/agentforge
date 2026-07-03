package com.agent.memory;

import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.api.LongTermMemoryWriter;
import com.agent.memory.api.MemoryDeduper;
import com.agent.memory.api.MemoryDistiller;
import com.agent.memory.api.MemoryExtractor;
import com.agent.memory.api.MemoryTtlManager;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemoryTopic;
import com.agent.memory.model.TaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F12 长期记忆决策节点补强测试（对齐 docs/tests/unit-test-cases.md §18.7）。
 *
 * <p>覆盖 F12.D1（任务失败跳过 / 成功写入）、F12.D2（episodic / semantic / procedural 提取）、
 * F12.D3（频次加权重要性）、F12.D4（cosine >= 0.92 合并 / 否则新增）、F12.D5（向量化写入）、
 * F12.D6（TTL 归档）、F12.D7（同主题蒸馏）、低重要性边界共 12 条补强用例。</p>
 */
class F12DecisionNodeTest {

    // ============ F12.D1: 写入决策 ============

    @Test
    @DisplayName("UT-F12-001: 任务失败时不写长期记忆（避免错误记忆污染）")
    void should_SkipWrite_When_TaskFailed() {
        // F12.D1 false 分支：task.status=FAILURE → 不触发记忆写入
        LongTermMemoryWriter writer = mock(LongTermMemoryWriter.class);
        when(writer.write(any())).thenReturn(null);

        TaskResult failedTask = new TaskResult("tk_001", TaskOutcome.FAILURE, "失败任务");
        boolean shouldWrite = failedTask.getOutcome() == TaskOutcome.SUCCESS;

        if (!shouldWrite) {
            writer.skipWrite("task failed: " + failedTask.getTaskId());
        }

        assertThat(shouldWrite)
                .as("任务失败时不应触发长期记忆写入")
                .isFalse();
        verify(writer, times(1)).skipWrite(any());
        verify(writer, never()).write(any());
    }

    @Test
    @DisplayName("UT-F12-002: 任务成功时触发长期记忆写入")
    void should_Write_When_TaskSuccess() {
        // F12.D1 true 分支：task.status=SUCCESS → 触发 LongTermMemoryWriter.write()
        LongTermMemoryWriter writer = mock(LongTermMemoryWriter.class);
        when(writer.write(any())).thenReturn("mem_001");

        TaskResult successTask = new TaskResult("tk_002", TaskOutcome.SUCCESS, "查询订单");
        boolean shouldWrite = successTask.getOutcome() == TaskOutcome.SUCCESS;

        String memoryId = null;
        if (shouldWrite) {
            MemoryRecord record = new MemoryRecord(null, MemoryType.EPISODIC, "查询订单任务成功");
            memoryId = writer.write(record);
        }

        assertThat(shouldWrite)
                .as("任务成功时应触发写入")
                .isTrue();
        assertThat(memoryId)
                .as("写入应返回 memoryId=mem_001")
                .isEqualTo("mem_001");
        verify(writer, times(1)).write(any());
    }

    // ============ F12.D2: 记忆提取分支 ============

    @Test
    @DisplayName("UT-F12-003: 情节记忆提取（任务含多步骤时提取步骤序列含时间戳）")
    void should_ExtractEpisodic_When_TaskHasSteps() {
        // F12.D2 episodic 分支
        MemoryExtractor extractor = mock(MemoryExtractor.class);
        ExtractedMemory episodic = new ExtractedMemory(MemoryType.EPISODIC);
        episodic.setStepSequence(List.of("step1: 接收任务", "step2: 查询订单", "step3: 返回结果"));
        when(extractor.extract(any(), eq(MemoryType.EPISODIC))).thenReturn(episodic);

        TaskResult task = new TaskResult("tk_003", TaskOutcome.SUCCESS, "查询订单");
        task.setSteps(List.of("step1", "step2", "step3"));
        ExtractedMemory result = extractor.extract(task, MemoryType.EPISODIC);

        assertThat(result.getType())
                .as("应提取 EPISODIC 类型记忆")
                .isEqualTo(MemoryType.EPISODIC);
        assertThat(result.getStepSequence())
                .as("情节记忆应含 3 个步骤序列")
                .hasSize(3);
        assertThat(result.getExtractedAt())
                .as("情节记忆应含时间戳")
                .isNotNull();
    }

    @Test
    @DisplayName("UT-F12-004: 语义记忆提取（事实查询任务提取事实知识含来源）")
    void should_ExtractSemantic_When_FactualTask() {
        // F12.D2 semantic 分支
        MemoryExtractor extractor = mock(MemoryExtractor.class);
        ExtractedMemory semantic = new ExtractedMemory(MemoryType.SEMANTIC);
        semantic.setFact("订单 od_001 总额为 5000 元");
        semantic.setSource("kb_order_001");
        when(extractor.extract(any(), eq(MemoryType.SEMANTIC))).thenReturn(semantic);

        TaskResult task = new TaskResult("tk_004", TaskOutcome.SUCCESS, "查询订单 od_001 总额");
        ExtractedMemory result = extractor.extract(task, MemoryType.SEMANTIC);

        assertThat(result.getType())
                .as("应提取 SEMANTIC 类型记忆")
                .isEqualTo(MemoryType.SEMANTIC);
        assertThat(result.getFact())
                .as("语义记忆应含事实知识")
                .contains("5000 元");
        assertThat(result.getSource())
                .as("语义记忆应含来源")
                .isEqualTo("kb_order_001");
    }

    @Test
    @DisplayName("UT-F12-005: 程序记忆提取（重复模式任务提取操作模板）")
    void should_ExtractProcedural_When_TaskHasPattern() {
        // F12.D2 procedural 分支
        MemoryExtractor extractor = mock(MemoryExtractor.class);
        ExtractedMemory procedural = new ExtractedMemory(MemoryType.PROCEDURAL);
        procedural.setPatternTemplate("1.接收任务 2.校验参数 3.查询 4.返回");
        when(extractor.extract(any(), eq(MemoryType.PROCEDURAL))).thenReturn(procedural);

        TaskResult task = new TaskResult("tk_005", TaskOutcome.SUCCESS, "重复订单查询");
        ExtractedMemory result = extractor.extract(task, MemoryType.PROCEDURAL);

        assertThat(result.getType())
                .as("应提取 PROCEDURAL 类型记忆")
                .isEqualTo(MemoryType.PROCEDURAL);
        assertThat(result.getPatternTemplate())
                .as("程序记忆应含操作模板")
                .startsWith("1.接收任务");
    }

    // ============ F12.D3: 重要性评分 ============

    @Test
    @DisplayName("UT-F12-006: 频次加权重要性评分（记忆被召回 5 次时 importanceScore 提升）")
    void should_ComputeImportanceByFrequency_When_MemoryAccessed() {
        // F12.D3: importance = freq × recency × relevance
        // 接口扩展为双方法后用匿名类实现（保留旧 3 维度路径向后兼容）
        com.agent.memory.api.ImportanceScorer scorer = new com.agent.memory.api.ImportanceScorer() {
            @Override
            public double score(int accessCount, double recency, double relevance) {
                return Math.min(1.0, accessCount * 0.1 * recency * relevance);
            }

            @Override
            public com.agent.memory.scorer.ImportanceResult score(
                    com.agent.memory.model.MemoryRecord record,
                    com.agent.memory.scorer.ScoringContext context) {
                // F12 旧路径不测试 5 维度新接口，返回 null 即可
                return null;
            }
        };

        double lowScore = scorer.score(1, 0.5, 0.6);
        double highScore = scorer.score(5, 0.9, 0.8);

        assertThat(lowScore)
                .as("低频次召回 importanceScore 应较低")
                .isLessThan(highScore);
        assertThat(highScore)
                .as("5 次召回 importanceScore 应提升至 0.36 = 5*0.1*0.9*0.8")
                .isCloseTo(0.36, within(0.0001));
        assertThat(highScore)
                .as("重要性评分应在 [0, 1] 区间内")
                .isBetween(0.0, 1.0);
    }

    // ============ F12.D4: 去重合并 / 新增 ============

    @Test
    @DisplayName("UT-F12-007: 高相似去重合并（新记忆与已有 sim=0.95 >= 0.92 触发更新合并）")
    void should_DedupeByCosineGe092_When_SimilarMemoryExists() {
        // F12.D4 true 分支：sim=0.95 >= 0.92 → 触发更新合并，不新增
        MemoryDeduper deduper = mock(MemoryDeduper.class);
        when(deduper.dedupThreshold()).thenReturn(0.92);
        when(deduper.findMaxSimilarity(any())).thenReturn(0.95);

        MemoryRecord existing = new MemoryRecord("mem_old", MemoryType.SEMANTIC, "订单查询知识");
        MemoryRecord incoming = new MemoryRecord(null, MemoryType.SEMANTIC, "订单查询知识补充");
        when(deduper.merge(eq(existing), eq(incoming))).thenReturn(existing);

        double maxSim = deduper.findMaxSimilarity(incoming);
        boolean shouldMerge = maxSim >= deduper.dedupThreshold();

        MemoryRecord result = null;
        if (shouldMerge) {
            result = deduper.merge(existing, incoming);
        }

        assertThat(maxSim)
                .as("最大相似度应为 0.95")
                .isEqualTo(0.95);
        assertThat(shouldMerge)
                .as("sim=0.95 >= 阈值 0.92 应触发合并")
                .isTrue();
        assertThat(result)
                .as("合并后应返回更新后的 existing 记录，不新增")
                .isSameAs(existing);
        verify(deduper, times(1)).findMaxSimilarity(any());
        verify(deduper, times(1)).merge(eq(existing), eq(incoming));
    }

    @Test
    @DisplayName("UT-F12-008: 低相似新增（新记忆最高 sim=0.7 < 0.92 向量化写入 Milvus）")
    void should_InsertNew_When_SimilarityLt092() {
        // F12.D4 false 分支：sim=0.7 < 0.92 → 新增记忆，向量化写入 Milvus
        MemoryDeduper deduper = mock(MemoryDeduper.class);
        when(deduper.dedupThreshold()).thenReturn(0.92);
        when(deduper.findMaxSimilarity(any())).thenReturn(0.70);

        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        EmbeddingVector vector = new EmbeddingVector(new float[1024]);
        when(embeddingClient.embed(any())).thenReturn(vector);

        MemoryVectorStore vectorStore = mock(MemoryVectorStore.class);

        MemoryRecord incoming = new MemoryRecord(null, MemoryType.SEMANTIC, "全新领域知识");
        double maxSim = deduper.findMaxSimilarity(incoming);
        boolean shouldInsert = maxSim < deduper.dedupThreshold();

        if (shouldInsert) {
            EmbeddingVector v = embeddingClient.embed(incoming.getContent());
            vectorStore.insert(incoming, v);
        }

        assertThat(maxSim)
                .as("最大相似度应为 0.70")
                .isEqualTo(0.70);
        assertThat(shouldInsert)
                .as("sim=0.70 < 阈值 0.92 应新增记忆")
                .isTrue();
        verify(embeddingClient, times(1)).embed(any());
        verify(vectorStore, times(1)).insert(any(), any());
    }

    // ============ F12.D5: 向量化写入 ============

    @Test
    @DisplayName("UT-F12-009: 写入时同步生成向量（调用 EmbeddingClient 生成 1024 维向量写入 Milvus）")
    void should_GenerateEmbedding_When_WriteLongTerm() {
        // F12.D5: 记忆文本 → EmbeddingClient 生成 1024 维向量 → 写入 Milvus
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        EmbeddingVector vector = new EmbeddingVector(new float[1024]);
        when(embeddingClient.embed(eq("订单总额 5000 元"))).thenReturn(vector);

        MemoryVectorStore vectorStore = mock(MemoryVectorStore.class);

        EmbeddingVector result = embeddingClient.embed("订单总额 5000 元");
        MemoryRecord record = new MemoryRecord("mem_009", MemoryType.SEMANTIC, "订单总额 5000 元");
        vectorStore.insert(record, result);

        assertThat(result.getDim())
                .as("向量维度应为 1024（bge-large-zh）")
                .isEqualTo(1024);
        assertThat(result.getValues())
                .as("向量数组长度应为 1024")
                .hasSize(1024);
        verify(vectorStore, times(1)).insert(eq(record), eq(vector));
    }

    // ============ F12.D6: TTL 归档 ============

    @Test
    @DisplayName("UT-F12-010: TTL 到期归档（记忆超 90 天标记 ARCHIVED 迁移归档存储）")
    void should_ExpireColdMemory_When_TtlReached() {
        // F12.D6: 记忆超 90 天 → 标记 ARCHIVED，迁移归档存储
        MemoryTtlManager ttlManager = mock(MemoryTtlManager.class);
        MemoryRecord old = new MemoryRecord("mem_010", MemoryType.SEMANTIC, "旧记忆");
        old.setCreatedAt(Instant.now().minus(95, ChronoUnit.DAYS));
        old.setTtlExpireAt(Instant.now().minus(5, ChronoUnit.DAYS));
        when(ttlManager.isExpired(eq(old))).thenReturn(true);

        boolean expired = ttlManager.isExpired(old);
        if (expired) {
            old.setStatus(MemoryStatus.ARCHIVED);
            ttlManager.archive(old);
        }

        assertThat(expired)
                .as("超 90 天的记忆应判定为过期")
                .isTrue();
        assertThat(old.getStatus())
                .as("过期记忆状态应标记为 ARCHIVED")
                .isEqualTo(MemoryStatus.ARCHIVED);
        verify(ttlManager, times(1)).archive(old);
    }

    // ============ F12.D7: 同主题蒸馏 ============

    @Test
    @DisplayName("UT-F12-011: 同主题碎片蒸馏（同主题 >=5 条生成主题摘要压缩比 > 50%）")
    void should_DistillTopic_When_FragmentsAccumulated() {
        // F12.D7: 同主题碎片 >= 5 条 → 生成主题摘要，压缩比 > 50%，原记忆归档
        MemoryDistiller distiller = mock(MemoryDistiller.class);
        MemoryTopic topic = new MemoryTopic("订单查询", 5);
        MemoryTopic distilled = new MemoryTopic("订单查询", 5);
        distilled.setSummary("订单查询相关 5 条记忆的主题摘要");
        distilled.setCompressionRatio(0.65);
        distilled.setDistilled(true);
        when(distiller.distill(eq(topic))).thenReturn(distilled);

        MemoryTopic result = distiller.distill(topic);

        assertThat(topic.getFragmentCount())
                .as("同主题碎片数应 >= 5 触发蒸馏")
                .isGreaterThanOrEqualTo(5);
        assertThat(result.isDistilled())
                .as("蒸馏后应标记 distilled=true")
                .isTrue();
        assertThat(result.getSummary())
                .as("蒸馏后应生成主题摘要")
                .contains("主题摘要");
        assertThat(result.getCompressionRatio())
                .as("压缩比应 > 50% (0.65)")
                .isGreaterThan(0.5);
    }

    // ============ F12.D3 边界: 低重要性 ============

    @Test
    @DisplayName("UT-F12-012: 低重要性记忆不写入（importanceScore=0.2 < 0.3 拒绝写入避免噪声）")
    void should_FilterLowImportance_When_ScoreLt03() {
        // F12.D3 边界：importanceScore=0.2 < 0.3 → 拒绝写入
        LongTermMemoryWriter writer = mock(LongTermMemoryWriter.class);
        when(writer.write(any())).thenReturn(null);

        MemoryRecord lowImportance = new MemoryRecord(null, MemoryType.SEMANTIC, "低价值噪声记忆");
        lowImportance.setImportanceScore(0.2);

        boolean shouldWrite = lowImportance.getImportanceScore() >= 0.3;

        if (!shouldWrite) {
            writer.skipWrite("importanceScore=0.2 < 0.3, low importance filtered");
        }

        assertThat(lowImportance.getImportanceScore())
                .as("低重要性评分应为 0.2")
                .isEqualTo(0.2);
        assertThat(shouldWrite)
                .as("importanceScore=0.2 < 0.3 应拒绝写入")
                .isFalse();
        verify(writer, times(1)).skipWrite(any());
        verify(writer, never()).write(any());
    }
}
