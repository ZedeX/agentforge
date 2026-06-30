package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ToolMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RiskClassifierImpl} 单元测试。
 */
class RiskClassifierImplTest {

    private final RiskClassifierImpl classifier = new RiskClassifierImpl();

    @Test
    @DisplayName("general + none: 分类 R1 (低风险, 无审批无沙箱)")
    void should_ClassifyR1_When_GeneralAndNone() {
        ToolMeta meta = new ToolMeta("tool_read", "read", ExecutorType.GENERAL, SideEffect.NONE);

        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level).isEqualTo(ToolRiskLevel.R1);
        assertThat(level.requiresApproval()).isFalse();
        assertThat(level.requiresSandbox()).isFalse();
    }

    @Test
    @DisplayName("proxy + reversible: 分类 R2 (中风险)")
    void should_ClassifyR2_When_ProxyAndReversible() {
        ToolMeta meta = new ToolMeta("tool_db", "db_update", ExecutorType.PROXY, SideEffect.REVERSIBLE);

        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level).isEqualTo(ToolRiskLevel.R2);
        assertThat(level.requiresApproval()).isFalse();
    }

    @Test
    @DisplayName("sandbox 执行器: 分类 R3 (高风险, 需审批需沙箱)")
    void should_ClassifyR3_When_SandboxExecutor() {
        ToolMeta meta = new ToolMeta("tool_exec", "exec", ExecutorType.SANDBOX, SideEffect.REVERSIBLE);

        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level).isEqualTo(ToolRiskLevel.R3);
        assertThat(level.requiresApproval()).isTrue();
        assertThat(level.requiresSandbox()).isTrue();
    }

    @Test
    @DisplayName("irreversible 副作用: 分类 R3 (无论执行器类型)")
    void should_ClassifyR3_When_IrreversibleSideEffect() {
        ToolMeta meta = new ToolMeta("tool_del", "delete", ExecutorType.PROXY, SideEffect.IRREVERSIBLE);

        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level).isEqualTo(ToolRiskLevel.R3);
        assertThat(level.requiresApproval()).isTrue();
    }

    @Test
    @DisplayName("null meta: 兜底分类 R3 (安全优先)")
    void should_ClassifyR3_When_NullMeta() {
        ToolRiskLevel level = classifier.classify(null);

        assertThat(level).isEqualTo(ToolRiskLevel.R3);
    }

    @Test
    @DisplayName("非标准组合 (general + reversible): 兜底 R2 (保守中风险)")
    void should_ClassifyR2_When_UnknownCombination() {
        ToolMeta meta = new ToolMeta("tool_mix", "mix", ExecutorType.GENERAL, SideEffect.REVERSIBLE);

        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level).isEqualTo(ToolRiskLevel.R2);
    }
}
