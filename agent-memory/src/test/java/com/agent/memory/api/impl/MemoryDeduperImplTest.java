package com.agent.memory.api.impl;

import com.agent.memory.config.MemoryProperties;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.DedupReport;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.repository.MemoryRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryDeduperImplTest {

    private MemoryDeduperImpl createDeduper(MemoryRecordRepository repository) {
        return new MemoryDeduperImpl(repository, new MemoryProperties());
    }

    // ============ findMaxSimilarity ============

    @Test
    @DisplayName("findMaxSimilarity 对仓库中不存在的 hash 应返回 0.0")
    void should_ReturnZero_When_HashNotInRepository() {
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        when(repository.findByContentHash(anyString())).thenReturn(Optional.empty());
        MemoryDeduperImpl deduper = createDeduper(repository);

        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "重复内容测试");
        record.setContentHash("hash_001");

        double sim = deduper.findMaxSimilarity(record);

        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    @DisplayName("findMaxSimilarity 对仓库中已存在的 hash 应返回 1.0")
    void should_ReturnOne_When_HashExistsInRepository() {
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryRecord existing = new MemoryRecord("mem_existing", MemoryType.SEMANTIC, "已有内容");
        when(repository.findByContentHash("hash_dup")).thenReturn(Optional.of(existing));
        MemoryDeduperImpl deduper = createDeduper(repository);

        MemoryRecord record = new MemoryRecord("mem_002", MemoryType.SEMANTIC, "相同内容");
        record.setContentHash("hash_dup");

        double sim = deduper.findMaxSimilarity(record);

        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    @DisplayName("findMaxSimilarity 对 null contentHash 应返回 0.0")
    void should_ReturnZero_When_ContentHashIsNull() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_003", MemoryType.SEMANTIC, "内容");

        double sim = deduper.findMaxSimilarity(record);

        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    @DisplayName("findMaxSimilarity 对 null record 应返回 0.0")
    void should_ReturnZero_When_RecordIsNull() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));
        assertThat(deduper.findMaxSimilarity(null)).isEqualTo(0.0);
    }

    // ============ merge ============

    @Test
    @DisplayName("merge 应合并内容并累加 recallCount")
    void should_MergeContentAndIncrementRecallCount_When_MergeInvoked() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));
        MemoryRecord existing = new MemoryRecord("mem_old", MemoryType.EPISODIC, "旧内容");
        existing.setRecallCount(2);
        MemoryRecord incoming = new MemoryRecord("mem_new", MemoryType.EPISODIC, "新内容");
        incoming.setRecallCount(3);

        MemoryRecord merged = deduper.merge(existing, incoming);

        assertThat(merged.getContent()).contains("旧内容").contains("新内容");
        assertThat(merged.getRecallCount()).isEqualTo(4); // max(2,3)+1
    }

    @Test
    @DisplayName("merge 对 null existing 应返回 incoming")
    void should_ReturnIncoming_When_ExistingIsNull() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));
        MemoryRecord incoming = new MemoryRecord("mem_new", MemoryType.EPISODIC, "新内容");

        MemoryRecord result = deduper.merge(null, incoming);

        assertThat(result).isSameAs(incoming);
    }

    // ============ dedup 批量去重 ============

    @Test
    @DisplayName("dedup 对空列表应返回全零报告")
    void should_ReturnAllZeros_When_BatchEmpty() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));

        DedupReport report = deduper.dedup(List.of());

        assertThat(report.getDropped()).isEqualTo(0);
        assertThat(report.getKept()).isEqualTo(0);
        assertThat(report.total()).isEqualTo(0);
    }

    @Test
    @DisplayName("dedup 对无重复的批次应全部保留")
    void should_KeepAll_When_NoDuplicates() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));

        MemoryRecord r1 = new MemoryRecord("mem_1", MemoryType.SEMANTIC, "内容A");
        r1.setContentHash("hash_a");
        MemoryRecord r2 = new MemoryRecord("mem_2", MemoryType.SEMANTIC, "内容B");
        r2.setContentHash("hash_b");

        DedupReport report = deduper.dedup(Arrays.asList(r1, r2));

        assertThat(report.getDropped()).isEqualTo(0);
        assertThat(report.getKept()).isEqualTo(2);
    }

    @Test
    @DisplayName("dedup 对相同 hash 的记录应丢弃较新的，保留最早的")
    void should_DropDuplicates_When_SameHash() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));

        MemoryRecord r1 = new MemoryRecord("mem_old", MemoryType.SEMANTIC, "相同内容");
        r1.setContentHash("hash_same");
        r1.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));

        MemoryRecord r2 = new MemoryRecord("mem_new", MemoryType.SEMANTIC, "相同内容");
        r2.setContentHash("hash_same");
        r2.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        MemoryRecord r3 = new MemoryRecord("mem_newest", MemoryType.SEMANTIC, "相同内容");
        r3.setContentHash("hash_same");
        r3.setCreatedAt(Instant.now());

        DedupReport report = deduper.dedup(Arrays.asList(r1, r2, r3));

        assertThat(report.getDropped()).isEqualTo(2);
        assertThat(report.getKept()).isEqualTo(1);
    }

    @Test
    @DisplayName("dedup 对 null hash 的记录应直接保留")
    void should_KeepRecords_When_HashIsNull() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));

        MemoryRecord r1 = new MemoryRecord("mem_nohash1", MemoryType.SEMANTIC, "无hash内容1");
        MemoryRecord r2 = new MemoryRecord("mem_nohash2", MemoryType.SEMANTIC, "无hash内容2");

        DedupReport report = deduper.dedup(Arrays.asList(r1, r2));

        assertThat(report.getDropped()).isEqualTo(0);
        assertThat(report.getKept()).isEqualTo(2);
    }

    @Test
    @DisplayName("dedup 对多组重复应正确统计")
    void should_HandleMultipleGroups_When_Dedup() {
        MemoryDeduperImpl deduper = createDeduper(mock(MemoryRecordRepository.class));

        // Group A: 2 records (1 dropped, 1 kept)
        MemoryRecord a1 = new MemoryRecord("a1", MemoryType.SEMANTIC, "内容A");
        a1.setContentHash("hash_a");
        a1.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        MemoryRecord a2 = new MemoryRecord("a2", MemoryType.SEMANTIC, "内容A");
        a2.setContentHash("hash_a");
        a2.setCreatedAt(Instant.now());

        // Group B: 3 records (2 dropped, 1 kept)
        MemoryRecord b1 = new MemoryRecord("b1", MemoryType.SEMANTIC, "内容B");
        b1.setContentHash("hash_b");
        b1.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        MemoryRecord b2 = new MemoryRecord("b2", MemoryType.SEMANTIC, "内容B");
        b2.setContentHash("hash_b");
        b2.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        MemoryRecord b3 = new MemoryRecord("b3", MemoryType.SEMANTIC, "内容B");
        b3.setContentHash("hash_b");
        b3.setCreatedAt(Instant.now());

        // Unique: 1 record (1 kept)
        MemoryRecord c1 = new MemoryRecord("c1", MemoryType.SEMANTIC, "内容C");
        c1.setContentHash("hash_c");

        DedupReport report = deduper.dedup(Arrays.asList(a1, a2, b1, b2, b3, c1));

        assertThat(report.getDropped()).isEqualTo(3); // 1 from group A + 2 from group B
        assertThat(report.getKept()).isEqualTo(3); // 1 from A + 1 from B + 1 from C
        assertThat(report.total()).isEqualTo(6);
    }

    // ============ sha256 ============

    @Test
    @DisplayName("sha256 应对相同输入返回相同哈希，且为 64 位十六进制")
    void should_ReturnSameHash_When_InputSame() {
        String h1 = MemoryDeduperImpl.sha256("abc");
        String h2 = MemoryDeduperImpl.sha256("abc");
        String h3 = MemoryDeduperImpl.sha256("abd");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
        assertThat(h1.length()).isEqualTo(64);
    }
}
