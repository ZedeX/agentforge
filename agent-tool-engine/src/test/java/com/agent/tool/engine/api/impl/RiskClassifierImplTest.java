package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.risk.PiiDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link RiskClassifierImpl} 单元测试 (T4 doc 05-tool-engine §4.2 / §4.3).
 *
 * <p>使用真实 {@link PiiDetector} + mock {@link ApprovalStore} 组合测试,
 * 覆盖 5 值 SideEffect 分级 / 不降级原则 / PII 升级 / R2 近期审批跳过
 * 共 10 条决策路径.</p>
 */
@ExtendWith(MockitoExtension.class)
class RiskClassifierImplTest {

    @Mock
    private ApprovalStore approvalStore;

    private RiskClassifierImpl classifier;

    @BeforeEach
    void setUp() {
        PiiDetector piiDetector = new PiiDetector();
        classifier = new RiskClassifierImpl(piiDetector, approvalStore);
    }

    // ==================== base level from SideEffect (5 值) ====================

    @Test
    @DisplayName("SideEffect=NONE: 分类 R1")
    void classify_none_returnsR1() {
        ToolMeta meta = new ToolMeta("t1", "n", ExecutorType.HTTP_API, SideEffect.NONE);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R1);
        assertThat(assessment.isRequiresApproval()).isFalse();
    }

    @Test
    @DisplayName("SideEffect=READ_ONLY: 分类 R1")
    void classify_readOnly_returnsR1() {
        ToolMeta meta = new ToolMeta("t2", "n", ExecutorType.HTTP_API, SideEffect.READ_ONLY);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R1);
        assertThat(assessment.isRequiresApproval()).isFalse();
    }

    @Test
    @DisplayName("SideEffect=WRITE_LOCAL: 分类 R2")
    void classify_writeLocal_returnsR2() {
        ToolMeta meta = new ToolMeta("t3", "n", ExecutorType.HTTP_API, SideEffect.WRITE_LOCAL);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R2);
    }

    @Test
    @DisplayName("SideEffect=WRITE_EXTERNAL: 分类 R3")
    void classify_writeExternal_returnsR3() {
        ToolMeta meta = new ToolMeta("t4", "n", ExecutorType.HTTP_API, SideEffect.WRITE_EXTERNAL);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.isRequiresApproval()).isTrue();
    }

    @Test
    @DisplayName("SideEffect=DESTRUCTIVE: 分类 R3")
    void classify_destructive_returnsR3() {
        ToolMeta meta = new ToolMeta("t5", "n", ExecutorType.HTTP_API, SideEffect.DESTRUCTIVE);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.isRequiresApproval()).isTrue();
    }

    // ==================== 不降级原则 (never downgrades) ====================

    @Test
    @DisplayName("声明 R3 + SideEffect=NONE: 不降级, 仍为 R3")
    void classify_neverDowngrades_whenDeclaredHigher() {
        ToolMeta meta = new ToolMeta("t6", "n", ExecutorType.HTTP_API,
                SideEffect.NONE, ToolRiskLevel.R3);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.getReason()).contains("declared=R3 upgrades");
    }

    @Test
    @DisplayName("声明 R1 + SideEffect=DESTRUCTIVE: 升级到 R3 (取 max)")
    void classify_upgrades_whenSideEffectHigher() {
        ToolMeta meta = new ToolMeta("t7", "n", ExecutorType.HTTP_API,
                SideEffect.DESTRUCTIVE, ToolRiskLevel.R1);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
    }

    // ==================== PII 强制升级 ====================

    @Test
    @DisplayName("R1 工具 + 参数含手机号: 强制升级 R3 + 审批")
    void classify_r1WithPii_boostsToR3() {
        ToolMeta meta = new ToolMeta("t8", "n", ExecutorType.HTTP_API, SideEffect.NONE);
        ToolCallRequest request = new ToolCallRequest("t8",
                "{\"phone\":\"13800138000\"}");

        RiskAssessment assessment = classifier.classify(meta, request);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.isRequiresApproval()).isTrue();
        assertThat(assessment.getReason()).contains("PII=PHONE");
    }

    @Test
    @DisplayName("R1 工具 + 参数含邮箱: 强制升级 R3")
    void classify_r1WithEmailPii_boostsToR3() {
        ToolMeta meta = new ToolMeta("t9", "n", ExecutorType.HTTP_API, SideEffect.NONE);
        ToolCallRequest request = new ToolCallRequest("t9",
                "{\"email\":\"user@example.com\"}");

        RiskAssessment assessment = classifier.classify(meta, request);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.getReason()).contains("PII=EMAIL");
    }

    @Test
    @DisplayName("R1 工具 + 参数含身份证: 强制升级 R3")
    void classify_r1WithIdCard_boostsToR3() {
        ToolMeta meta = new ToolMeta("t10", "n", ExecutorType.HTTP_API, SideEffect.NONE);
        ToolCallRequest request = new ToolCallRequest("t10",
                "{\"id\":\"110101199001011234\"}");

        RiskAssessment assessment = classifier.classify(meta, request);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.getReason()).contains("PII=ID_CARD");
    }

    @Test
    @DisplayName("R1 工具 + 参数含 API key: 强制升级 R3")
    void classify_r1WithApiKey_boostsToR3() {
        ToolMeta meta = new ToolMeta("t11", "n", ExecutorType.HTTP_API, SideEffect.NONE);
        // sk- + 40 个字母数字
        ToolCallRequest request = new ToolCallRequest("t11",
                "{\"key\":\"sk-"
                        + "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJN\"}");

        RiskAssessment assessment = classifier.classify(meta, request);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.getReason()).contains("PII=API_KEY");
    }

    // ==================== R2 审批决策 (近期审批跳过) ====================

    @Test
    @DisplayName("R2 + 无近期审批: requiresApproval=true")
    void classify_r2NoRecentApproval_requiresApproval() {
        ToolMeta meta = new ToolMeta("t12", "n", ExecutorType.HTTP_API, SideEffect.WRITE_LOCAL);
        ToolCallRequest request = new ToolCallRequest("t12", "{}");
        request.setTenantId("tn_1");
        request.setInputHash("hash_1");
        when(approvalStore.findRecentApproved("tn_1", "t12", "hash_1",
                Duration.ofHours(1))).thenReturn(Optional.empty());

        RiskAssessment assessment = classifier.classify(meta, request);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R2);
        assertThat(assessment.isRequiresApproval()).isTrue();
        assertThat(assessment.getReason()).contains("R2 no recent approval");
    }

    @Test
    @DisplayName("R2 + 同租户 1h 内同 toolId 同 paramsHash 已批: requiresApproval=false")
    void classify_r2RecentApproval_skipsApproval() {
        ToolMeta meta = new ToolMeta("t13", "n", ExecutorType.HTTP_API, SideEffect.WRITE_LOCAL);
        ToolCallRequest request = new ToolCallRequest("t13", "{}");
        request.setTenantId("tn_1");
        request.setInputHash("hash_x");
        ApprovalRecord recent = new ApprovalRecord();
        when(approvalStore.findRecentApproved("tn_1", "t13", "hash_x",
                Duration.ofHours(1))).thenReturn(Optional.of(recent));

        RiskAssessment assessment = classifier.classify(meta, request);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R2);
        assertThat(assessment.isRequiresApproval()).isFalse();
        assertThat(assessment.getReason()).contains("R2 recent approval within 1h");
    }

    // ==================== Null / 兜底 ====================

    @Test
    @DisplayName("null meta: 兜底 R3 + 审批")
    void classify_nullMeta_returnsR3() {
        RiskAssessment assessment = classifier.classify(null, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
        assertThat(assessment.isRequiresApproval()).isTrue();
        assertThat(assessment.getReason()).contains("null meta safety fallback");
    }

    @Test
    @DisplayName("R1 + 无 request: 默认无需审批")
    void classify_r1NoRequest_noApproval() {
        ToolMeta meta = new ToolMeta("t14", "n", ExecutorType.HTTP_API, SideEffect.NONE);

        RiskAssessment assessment = classifier.classify(meta, null);

        assertThat(assessment.getRiskLevel()).isEqualTo(ToolRiskLevel.R1);
        assertThat(assessment.isRequiresApproval()).isFalse();
    }

    @Test
    @DisplayName("default classify(meta) 方法返回 ToolRiskLevel (向后兼容)")
    void classify_defaultMethod_returnsLevel() {
        ToolMeta meta = new ToolMeta("t15", "n", ExecutorType.HTTP_API, SideEffect.DESTRUCTIVE);

        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level).isEqualTo(ToolRiskLevel.R3);
    }
}
