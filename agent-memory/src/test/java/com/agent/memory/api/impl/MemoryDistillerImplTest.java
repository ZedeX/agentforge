package com.agent.memory.api.impl;

import com.agent.memory.model.MemoryTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryDistillerImplTest {

    @Test
    @DisplayName("distill 在碎片数 >= 5 时应生成摘要并标记 distilled=true")
    void should_DistillSummary_When_FragmentsReachThreshold() {
        MemoryDistillerImpl distiller = new MemoryDistillerImpl();
        MemoryTopic topic = new MemoryTopic("订单查询", 5);

        MemoryTopic result = distiller.distill(topic);

        assertThat(result.isDistilled()).isTrue();
        assertThat(result.getSummary()).contains("订单查询").contains("5");
        assertThat(result.getCompressionRatio()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("distill 在碎片数 < 5 时应跳过蒸馏保持原状")
    void should_SkipDistill_When_FragmentsBelowThreshold() {
        MemoryDistillerImpl distiller = new MemoryDistillerImpl();
        MemoryTopic topic = new MemoryTopic("支付", 3);

        MemoryTopic result = distiller.distill(topic);

        assertThat(result.isDistilled()).isFalse();
        assertThat(result.getSummary()).isNull();
    }

    @Test
    @DisplayName("distill 对 null 入参应返回 null")
    void should_ReturnNull_When_TopicIsNull() {
        MemoryDistillerImpl distiller = new MemoryDistillerImpl();
        assertThat(distiller.distill(null)).isNull();
    }
}