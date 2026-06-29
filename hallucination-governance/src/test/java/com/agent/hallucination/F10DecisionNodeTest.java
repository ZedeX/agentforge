package com.agent.hallucination;

import com.agent.hallucination.api.HallucinationMetricWriter;
import com.agent.hallucination.api.L4HardValidator;
import com.agent.hallucination.api.RagAnchor;
import com.agent.hallucination.api.SelfCheckEngine;
import com.agent.hallucination.api.ToolGatewayGuard;
import com.agent.hallucination.enums.GuardResult;
import com.agent.hallucination.enums.HallucinationLayer;
import com.agent.hallucination.enums.SelfCheckResult;
import com.agent.hallucination.model.Claim;
import com.agent.hallucination.model.HallucinationEvent;
import com.agent.hallucination.model.HallucinationMetric;
import com.agent.hallucination.model.ToolCallGuardRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F10 幻觉治理决策节点补强测试（对齐 docs/tests/unit-test-cases.md §18.5）。
 *
 * <p>覆盖 F10 六层架构中的 L2（分步自检）、L4（L4-1 硬校验兜底）、
 * L5（工具网关前置拦截）、L6（幻觉率指标追踪）共 4 条补强用例。</p>
 */
class F10DecisionNodeTest {

    @Test
    @DisplayName("UT-F10-001: 第二层分步自检触发（每步产出含事实声明时触发自检，无来源标注判疑似幻觉）")
    void should_ApplyLayer2SelfCheck_When_StepProducesClaim() {
        // F10 L2: 每步产出含事实声明 → 触发自检
        // 注意: Claim.equals() 基于 claimId（此处均为 null），不能用 eq() 桩接，
        // 改用 thenAnswer 按入参 hasSourceTag 返回不同结果。
        SelfCheckEngine engine = mock(SelfCheckEngine.class);
        when(engine.check(any())).thenAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            return c.isHasSourceTag() ? SelfCheckResult.PASS : SelfCheckResult.SUSPECTED;
        });

        Claim withSource = new Claim("订单总额为 5000 元", true);
        withSource.setSourceRef("kb_order_001");

        Claim withoutSource = new Claim("用户信用分为 850 分", false);

        // 1. 含来源标注的自检通过
        SelfCheckResult passResult = engine.check(withSource);
        assertThat(passResult)
                .as("含来源标注的 claim 应通过自检")
                .isEqualTo(SelfCheckResult.PASS);

        // 2. 无来源标注判定疑似幻觉，注入反思提示
        SelfCheckResult suspectedResult = engine.check(withoutSource);
        assertThat(suspectedResult)
                .as("无来源标注的 claim 应判定为疑似幻觉")
                .isEqualTo(SelfCheckResult.SUSPECTED);
        assertThat(withoutSource.isHasSourceTag())
                .as("无来源标注的 claim hasSourceTag 应为 false")
                .isFalse();
    }

    @Test
    @DisplayName("UT-F10-002: 第四层 L4-1 硬校验兜底（L3 RAG 召回不足时进入 L4-1 按规则拦截）")
    void should_ApplyLayer4L4Hard_When_L3AnchorRanOut() {
        // F10 L4: L3 RAG 召回不足 → 进入 L4-1 硬校验
        RagAnchor ragAnchor = mock(RagAnchor.class);
        when(ragAnchor.anchor(eq("冷门事实查询"))).thenReturn(false);

        L4HardValidator hardValidator = mock(L4HardValidator.class);
        // 无来源标签的输出应被 L4-1 拦截
        when(hardValidator.validate(eq("答案是 42"))).thenReturn(false);
        // 含来源标签的输出应通过
        when(hardValidator.validate(eq("答案是 42 [来源:kb_001]"))).thenReturn(true);

        // 1. L3 RAG 召回不足
        boolean anchored = ragAnchor.anchor("冷门事实查询");
        assertThat(anchored)
                .as("L3 RAG 召回不足应返回 false")
                .isFalse();

        // 2. 进入 L4-1 硬校验：无来源标签拦截
        boolean noTag = hardValidator.validate("答案是 42");
        assertThat(noTag)
                .as("无来源标签输出应被 L4-1 拦截")
                .isFalse();

        // 3. 含来源标签通过
        boolean withTag = hardValidator.validate("答案是 42 [来源:kb_001]");
        assertThat(withTag)
                .as("含来源标签输出应通过 L4-1 硬校验")
                .isTrue();
    }

    @Test
    @DisplayName("UT-F10-003: 第五层工具网关前置拦截（工具参数 Schema 不匹配返回 REJECTED）")
    void should_ApplyLayer5ToolGuard_When_ToolCallRequested() {
        // F10 L5: 工具参数 Schema 不匹配 → ToolGatewayGuard 拦截
        ToolGatewayGuard guard = mock(ToolGatewayGuard.class);
        Map<String, Object> incompleteParams = new HashMap<>();
        // 缺 orderId 字段
        incompleteParams.put("tenantId", "tn_1");
        ToolCallGuardRequest badRequest = new ToolCallGuardRequest("tool_order", incompleteParams);
        badRequest.setRequiredFields(List.of("orderId", "tenantId"));
        when(guard.guard(eq(badRequest))).thenReturn(GuardResult.REJECTED);

        Map<String, Object> validParams = new HashMap<>();
        validParams.put("orderId", "od_1");
        validParams.put("tenantId", "tn_1");
        ToolCallGuardRequest goodRequest = new ToolCallGuardRequest("tool_order", validParams);
        goodRequest.setRequiredFields(List.of("orderId", "tenantId"));
        when(guard.guard(eq(goodRequest))).thenReturn(GuardResult.ALLOWED);

        // 1. 参数不匹配 → 拦截
        GuardResult rejected = guard.guard(badRequest);
        assertThat(rejected)
                .as("参数 Schema 不匹配应被工具网关拦截")
                .isEqualTo(GuardResult.REJECTED);
        assertThat(badRequest.getRequiredFields())
                .as("缺失的 required 字段应为 orderId")
                .anyMatch("orderId"::equals);

        // 2. 参数完整 → 放行
        GuardResult allowed = guard.guard(goodRequest);
        assertThat(allowed)
                .as("参数完整匹配 Schema 应放行工具调用")
                .isEqualTo(GuardResult.ALLOWED);
    }

    @Test
    @DisplayName("UT-F10-004: 第六层幻觉率指标追踪（检测到幻觉事件时写入 agent_metrics_daily）")
    void should_ApplyLayer6Metric_When_HallucinationDetected() {
        // F10 L6: 检测到幻觉事件 → 写入 hallucination_rate 指标
        HallucinationMetricWriter writer = mock(HallucinationMetricWriter.class);

        HallucinationEvent event = new HallucinationEvent(
                HallucinationLayer.L2_SELF_CHECK,
                "无来源标注的事实声明",
                0.85);
        event.setTenantId("tn_1");
        event.setAgentId("ag_1");

        // 计算幻觉率：1 次幻觉 / 10 次总 claim = 0.10
        HallucinationMetric metric = new HallucinationMetric("tn_1", "ag_1", 0.10);
        metric.setTotalClaims(10);
        metric.setHallucinationCount(1);
        writer.write(metric);

        verify(writer, times(1)).write(any(HallucinationMetric.class));
        assertThat(metric.getHallucinationRate())
                .as("幻觉率应 = hallucinationCount / totalClaims = 0.10")
                .isEqualTo(0.10);
        assertThat(metric.getHallucinationCount())
                .as("幻觉次数应为 1")
                .isEqualTo(1L);
        assertThat(event.getDetectedLayer())
                .as("幻觉事件检测层应为 L2_SELF_CHECK")
                .isEqualTo(HallucinationLayer.L2_SELF_CHECK);
        assertThat(event.getSeverity())
                .as("幻觉事件严重度应为 0.85")
                .isEqualTo(0.85);
    }
}
