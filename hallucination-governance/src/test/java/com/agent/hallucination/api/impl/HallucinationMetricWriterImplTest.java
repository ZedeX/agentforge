package com.agent.hallucination.api.impl;

import com.agent.hallucination.model.HallucinationMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link HallucinationMetricWriterImpl} 单元测试。
 */
class HallucinationMetricWriterImplTest {

    private final HallucinationMetricWriterImpl writer = new HallucinationMetricWriterImpl();

    @Test
    @DisplayName("写入单条指标: 快照聚合正确")
    void shouldWriteSingleMetric() {
        HallucinationMetric m = new HallucinationMetric("t1", "a1", 0.1);
        m.setTotalClaims(100);
        m.setHallucinationCount(10);

        writer.write(m);

        HallucinationMetric snapshot = writer.getSnapshot("t1", "a1", m.getStatDate());
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getTotalClaims()).isEqualTo(100);
        assertThat(snapshot.getHallucinationCount()).isEqualTo(10);
        assertThat(writer.snapshotSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("写入多条同键指标: 累加并重算 rate")
    void shouldMergeMetricsOnSameKey() {
        LocalDate date = LocalDate.now();
        HallucinationMetric m1 = new HallucinationMetric("t1", "a1", 0.0);
        m1.setStatDate(date);
        m1.setTotalClaims(100);
        m1.setHallucinationCount(10);

        HallucinationMetric m2 = new HallucinationMetric("t1", "a1", 0.0);
        m2.setStatDate(date);
        m2.setTotalClaims(50);
        m2.setHallucinationCount(5);

        writer.write(m1);
        writer.write(m2);

        HallucinationMetric snapshot = writer.getSnapshot("t1", "a1", date);
        assertThat(snapshot.getTotalClaims()).isEqualTo(150);
        assertThat(snapshot.getHallucinationCount()).isEqualTo(15);
        assertThat(snapshot.getHallucinationRate()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    @DisplayName("负数计数容错: safe 归零后合并")
    void shouldTolerateNegativeCounts() {
        HallucinationMetric m = new HallucinationMetric("t2", "a2", 0.0);
        m.setTotalClaims(-5);
        m.setHallucinationCount(-2);

        writer.write(m);

        HallucinationMetric snap = writer.getSnapshot("t2", "a2", m.getStatDate());
        assertThat(snap.getTotalClaims()).isZero();
        assertThat(snap.getHallucinationCount()).isZero();
    }

    @Test
    @DisplayName("statDate 为 null: 用今天日期建键, 仍可读取")
    void shouldHandleNullStatDate() {
        HallucinationMetric m = new HallucinationMetric();
        m.setTenantId("t3");
        m.setAgentId("a3");
        m.setTotalClaims(1);
        m.setHallucinationCount(0);

        writer.write(m);

        HallucinationMetric snap = writer.getSnapshot("t3", "a3", null);
        assertThat(snap).isNotNull();
        assertThat(snap.getTotalClaims()).isEqualTo(1);
    }

    @Test
    @DisplayName("写入 null metric: 跳过, 快照为空")
    void shouldSkipNullMetric() {
        writer.write(null);
        assertThat(writer.snapshotSize()).isZero();
    }
}