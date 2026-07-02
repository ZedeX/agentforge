package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryDeduperImplTest {

    @Test
    @DisplayName("findMaxSimilarity 首次见到的内容应返回 0.0 并登记")
    void should_ReturnZero_When_ContentSeenFirstTime() {
        MemoryDeduperImpl deduper = new MemoryDeduperImpl();
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "重复内容测试");

        double sim = deduper.findMaxSimilarity(record);

        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    @DisplayName("findMaxSimilarity 第二次见到相同内容应返回 1.0")
    void should_ReturnOne_When_ContentSeenSecondTime() {
        MemoryDeduperImpl deduper = new MemoryDeduperImpl();
        MemoryRecord r1 = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "相同内容");
        MemoryRecord r2 = new MemoryRecord("mem_002", MemoryType.SEMANTIC, "相同内容");

        deduper.findMaxSimilarity(r1);
        double sim = deduper.findMaxSimilarity(r2);

        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    @DisplayName("merge 应合并内容并累加 recallCount")
    void should_MergeContentAndIncrementRecallCount_When_MergeInvoked() {
        MemoryDeduperImpl deduper = new MemoryDeduperImpl();
        MemoryRecord existing = new MemoryRecord("mem_old", MemoryType.EPISODIC, "旧内容");
        existing.setRecallCount(2);
        MemoryRecord incoming = new MemoryRecord("mem_new", MemoryType.EPISODIC, "新内容");
        incoming.setRecallCount(3);

        MemoryRecord merged = deduper.merge(existing, incoming);

        assertThat(merged.getContent()).contains("旧内容").contains("新内容");
        assertThat(merged.getRecallCount()).isEqualTo(4); // max(2,3)+1
    }

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