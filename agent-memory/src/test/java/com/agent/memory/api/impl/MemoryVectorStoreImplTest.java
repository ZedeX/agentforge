package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemorySearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MemoryVectorStoreImpl} 单测。
 *
 * <p>覆盖 insert（F12 骨架）+ search/delete/cosineSimilarity（Plan 03 T10）。
 * 重点覆盖 search 的多分支：null/empty 入参、tenantId 过滤、status 过滤、
 * scoreThreshold 过滤、topK 截断、零范数向量、维度不一致等。
 */
@DisplayName("MemoryVectorStoreImpl 内存向量存储（F12 + T10）")
class MemoryVectorStoreImplTest {

    // ===== insert（F12 骨架）=====

    @Test
    @DisplayName("insert 应能将 MemoryRecord 与 EmbeddingVector 存入内部存储")
    void should_StoreRecordAndVector_When_InsertInvoked() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "测试内容");
        EmbeddingVector vector = new EmbeddingVector(new float[]{0.1f, 0.2f, 0.3f});

        store.insert(record, vector);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("mem_001")).isSameAs(record);
        assertThat(store.findVector("mem_001")).isSameAs(vector);
    }

    @Test
    @DisplayName("insert 对 null 或空 memoryId 应跳过写入并保持存储为空")
    void should_SkipInsert_When_MemoryIdIsNullOrEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        EmbeddingVector vector = new EmbeddingVector(new float[]{0.1f});

        store.insert(null, vector);
        store.insert(new MemoryRecord(null, MemoryType.EPISODIC, "无ID"), vector);

        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("多次 insert 同一 memoryId 应覆盖旧记录")
    void should_OverwriteRecord_When_InsertSameIdTwice() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MemoryRecord r1 = new MemoryRecord("mem_dup", MemoryType.SEMANTIC, "v1");
        MemoryRecord r2 = new MemoryRecord("mem_dup", MemoryType.SEMANTIC, "v2");
        EmbeddingVector v1 = new EmbeddingVector(new float[]{1.0f});

        store.insert(r1, v1);
        store.insert(r2, v1);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("mem_dup").getContent()).isEqualTo("v2");
    }

    // ===== search（T10）=====

    @Test
    @DisplayName("search 对 null queryVector 应返回空列表")
    void should_ReturnEmpty_When_QueryVectorIsNull() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        List<MemorySearchHit> hits = store.search(null, "t1", 5, 0.0, MemoryStatus.ACTIVE);
        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("search 对空 queryVector 应返回空列表")
    void should_ReturnEmpty_When_QueryVectorIsEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        List<MemorySearchHit> hits = store.search(new float[0], "t1", 5, 0.0, MemoryStatus.ACTIVE);
        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("search 对 topK <= 0 应返回空列表")
    void should_ReturnEmpty_When_TopKNonPositive() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        float[] q = new float[]{0.1f, 0.2f};
        assertThat(store.search(q, null, 0, 0.0)).isEmpty();
        assertThat(store.search(q, null, -1, 0.0)).isEmpty();
    }

    @Test
    @DisplayName("search 应按余弦相似度降序返回命中结果")
    void should_ReturnSortedHits_When_SearchWithValidQuery() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        // 三个向量：q 与 v1 完全同向(1.0)，与 v2 同向(0.5)，与 v3 正交(0.0)
        MemoryRecord r1 = buildActiveRecord("m1", "t1", "r1");
        MemoryRecord r2 = buildActiveRecord("m2", "t1", "r2");
        MemoryRecord r3 = buildActiveRecord("m3", "t1", "r3");
        store.insert(r1, new EmbeddingVector(new float[]{1.0f, 0.0f}));
        store.insert(r2, new EmbeddingVector(new float[]{1.0f, 1.0f}));
        store.insert(r3, new EmbeddingVector(new float[]{0.0f, 1.0f}));

        float[] query = new float[]{1.0f, 0.0f};
        List<MemorySearchHit> hits = store.search(query, "t1", 5, 0.0, MemoryStatus.ACTIVE);

        assertThat(hits).hasSize(3);
        // 完全匹配 r1 score=1.0
        assertThat(hits.get(0).getRecord().getMemoryId()).isEqualTo("m1");
        assertThat(hits.get(0).getScore()).isCloseTo(1.0, within(1e-6));
        // r2 余弦=0.7071
        assertThat(hits.get(1).getRecord().getMemoryId()).isEqualTo("m2");
        // r3 余弦=0.0（截断后仍为 0.0，因为 threshold=0.0 不被过滤）
        assertThat(hits.get(2).getRecord().getMemoryId()).isEqualTo("m3");
        assertThat(hits.get(2).getScore()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("search 应按 tenantId 过滤跨租户记录")
    void should_FilterByTenantId_When_TenantIdProvided() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "tenantA", "r1"),
                new EmbeddingVector(new float[]{1.0f}));
        store.insert(buildActiveRecord("m2", "tenantB", "r2"),
                new EmbeddingVector(new float[]{1.0f}));

        List<MemorySearchHit> hits = store.search(new float[]{1.0f}, "tenantA", 5, 0.0, MemoryStatus.ACTIVE);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getRecord().getMemoryId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("search 当 tenantId 为 null 时应跨所有租户检索")
    void should_SearchAllTenants_When_TenantIdIsNull() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "tenantA", "r1"),
                new EmbeddingVector(new float[]{1.0f}));
        store.insert(buildActiveRecord("m2", "tenantB", "r2"),
                new EmbeddingVector(new float[]{1.0f}));

        List<MemorySearchHit> hits = store.search(new float[]{1.0f}, null, 5, 0.0, MemoryStatus.ACTIVE);

        assertThat(hits).hasSize(2);
    }

    @Test
    @DisplayName("search 应按 status 过滤非匹配记录")
    void should_FilterByStatus_When_StatusesProvided() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MemoryRecord active = buildActiveRecord("m1", "t1", "r1");
        MemoryRecord archived = buildRecordWithStatus("m2", "t1", "r2", MemoryStatus.ARCHIVED);
        store.insert(active, new EmbeddingVector(new float[]{1.0f}));
        store.insert(archived, new EmbeddingVector(new float[]{1.0f}));

        // 只查 ACTIVE
        List<MemorySearchHit> activeOnly = store.search(new float[]{1.0f}, "t1", 5, 0.0, MemoryStatus.ACTIVE);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).getRecord().getMemoryId()).isEqualTo("m1");

        // 查 ACTIVE + ARCHIVED
        List<MemorySearchHit> both = store.search(new float[]{1.0f}, "t1", 5, 0.0,
                MemoryStatus.ACTIVE, MemoryStatus.ARCHIVED);
        assertThat(both).hasSize(2);
    }

    @Test
    @DisplayName("search 当 statuses 为 null/空时应不限制 status")
    void should_NotFilterStatus_When_StatusesEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f}));
        store.insert(buildRecordWithStatus("m2", "t1", "r2", MemoryStatus.DISTILLED),
                new EmbeddingVector(new float[]{1.0f}));

        // null statuses
        assertThat(store.search(new float[]{1.0f}, "t1", 5, 0.0, (MemoryStatus[]) null)).hasSize(2);
        // empty statuses
        assertThat(store.search(new float[]{1.0f}, "t1", 5, 0.0)).hasSize(2);
    }

    @Test
    @DisplayName("search 应过滤低于 scoreThreshold 的结果")
    void should_FilterByScoreThreshold_When_ThresholdHigh() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f, 0.0f}));  // cos=1.0
        store.insert(buildActiveRecord("m2", "t1", "r2"),
                new EmbeddingVector(new float[]{1.0f, 1.0f}));  // cos≈0.707

        // threshold=0.9 只保留 m1
        List<MemorySearchHit> hits = store.search(new float[]{1.0f, 0.0f}, "t1", 5, 0.9, MemoryStatus.ACTIVE);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getRecord().getMemoryId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("search 应截断到 topK 条结果")
    void should_TruncateToTopK_When_MoreHitsThanTopK() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        for (int i = 0; i < 10; i++) {
            store.insert(buildActiveRecord("m" + i, "t1", "r" + i),
                    new EmbeddingVector(new float[]{1.0f}));
        }

        List<MemorySearchHit> hits = store.search(new float[]{1.0f}, "t1", 3, 0.0, MemoryStatus.ACTIVE);
        assertThat(hits).hasSize(3);
    }

    @Test
    @DisplayName("search 应跳过向量为 null 的条目")
    void should_SkipEntries_When_VectorIsNull() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f}));
        // 手动插入一个 vector=null 的条目（通过 insert null vector）
        store.insert(buildActiveRecord("m2", "t1", "r2"), null);

        List<MemorySearchHit> hits = store.search(new float[]{1.0f}, "t1", 5, 0.0, MemoryStatus.ACTIVE);
        // m2 向量为 null 应被跳过，只返回 m1
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getRecord().getMemoryId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("search 对零范数向量应返回 0 分")
    void should_ReturnZeroScore_When_VectorNormIsZero() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        // 全零向量，范数为 0
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{0.0f, 0.0f}));

        // threshold=0.0，零范数应得 0 分且不被过滤（0 >= 0）
        List<MemorySearchHit> hits = store.search(new float[]{1.0f, 0.0f}, "t1", 5, 0.0, MemoryStatus.ACTIVE);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getScore()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("search 对维度不一致的向量应返回 0 分")
    void should_ReturnZeroScore_When_VectorLengthsDiffer() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        // 存入 3 维向量
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}));

        // 用 2 维 query 检索 → 维度不一致 → 0 分
        List<MemorySearchHit> hits = store.search(new float[]{1.0f, 0.0f}, "t1", 5, 0.0, MemoryStatus.ACTIVE);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getScore()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("search 对空存储应返回空列表")
    void should_ReturnEmpty_When_StoreIsEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        List<MemorySearchHit> hits = store.search(new float[]{1.0f}, "t1", 5, 0.0, MemoryStatus.ACTIVE);
        assertThat(hits).isEmpty();
    }

    // ===== delete（T10）=====

    @Test
    @DisplayName("delete 应按 memoryId 删除条目")
    void should_DeleteEntry_When_DeleteByMemoryId() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f}));
        assertThat(store.size()).isEqualTo(1);

        store.delete("m1");

        assertThat(store.size()).isZero();
        assertThat(store.find("m1")).isNull();
    }

    @Test
    @DisplayName("delete 对 null memoryId 应无副作用")
    void should_DoNothing_When_DeleteWithNullId() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f}));

        store.delete(null);
        store.delete("");

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("delete 不存在的 memoryId 应无副作用")
    void should_DoNothing_When_DeleteNonExistentId() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        store.insert(buildActiveRecord("m1", "t1", "r1"),
                new EmbeddingVector(new float[]{1.0f}));

        store.delete("non_existent");

        assertThat(store.size()).isEqualTo(1);
    }

    // ===== 辅助方法 =====

    private static MemoryRecord buildActiveRecord(String id, String tenantId, String content) {
        return buildRecordWithStatus(id, tenantId, content, MemoryStatus.ACTIVE);
    }

    private static MemoryRecord buildRecordWithStatus(String id, String tenantId, String content,
                                                      MemoryStatus status) {
        MemoryRecord r = new MemoryRecord(id, MemoryType.SEMANTIC, content);
        r.setTenantId(tenantId);
        r.setStatus(status);
        return r;
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
