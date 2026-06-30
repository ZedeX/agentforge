package com.agent.drift.api.impl;

import com.agent.drift.model.BehaviorBaseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaselineAnchorImpl} 单元测试。
 */
class BaselineAnchorImplTest {

    private final BaselineAnchorImpl anchor = new BaselineAnchorImpl();

    @Test
    @DisplayName("首次锚定: 返回 true 并写入")
    void shouldAnchorOnFirstRun() {
        BehaviorBaseline baseline = new BehaviorBaseline("a1", "v1", "hash-001");

        assertThat(anchor.anchor(baseline)).isTrue();
        assertThat(anchor.getBaseline("a1")).isSameAs(baseline);
    }

    @Test
    @DisplayName("重复锚定同一 agentId: 返回 false 不覆盖")
    void shouldNotOverwriteExistingBaseline() {
        BehaviorBaseline first = new BehaviorBaseline("a1", "v1", "hash-001");
        BehaviorBaseline second = new BehaviorBaseline("a1", "v2", "hash-002");

        assertThat(anchor.anchor(first)).isTrue();
        assertThat(anchor.anchor(second)).isFalse();
        assertThat(anchor.getBaseline("a1").getVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("空 baseline / 空 agentId: 返回 false")
    void shouldRejectNullBaseline() {
        assertThat(anchor.anchor(null)).isFalse();
        BehaviorBaseline noAgent = new BehaviorBaseline();
        noAgent.setVersion("v1");
        assertThat(anchor.anchor(noAgent)).isFalse();
    }
}