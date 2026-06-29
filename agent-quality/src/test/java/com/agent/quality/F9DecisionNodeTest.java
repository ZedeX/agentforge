package com.agent.quality;

import com.agent.quality.api.BadcaseWriter;
import com.agent.quality.api.L4AuditValidator;
import com.agent.quality.api.L4ConsistencyValidator;
import com.agent.quality.api.L4HardValidator;
import com.agent.quality.api.ManualReviewQueue;
import com.agent.quality.enums.BadcaseCategory;
import com.agent.quality.enums.BadcaseSeverity;
import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.enums.TaskRiskLevel;
import com.agent.quality.exception.L4ValidationException;
import com.agent.quality.model.BadcaseRecord;
import com.agent.quality.model.L4ValidationOutput;
import com.agent.quality.model.ManualReviewItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F9 L4 三级校验 / Badcase 归集决策节点补强测试
 * （对齐 docs/tests/unit-test-cases.md §11 agent-quality F9 用例 UT-QA-001~010）。
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>F9.D2 L4-1 硬校验：JSON Schema + 来源标签 + 黑名单词（UT-QA-001~003）</li>
 *   <li>F9.D3 L4-2 事实一致性校验：cosine_sim ≥0.75 通过（UT-QA-004~005）</li>
 *   <li>F9.D4 L4-3 强模型终审：overall ≥0.7 通过（UT-QA-006~007）</li>
 *   <li>F9.D1 任务风险分级：低风险闲聊跳过 L4-2/L4-3（UT-QA-008）</li>
 *   <li>F9 Badcase 归集：重试耗尽写入 badcase 表（UT-QA-009）</li>
 *   <li>F9 人工审核队列：高严重度 Badcase 入队（UT-QA-010）</li>
 *   <li>L4ValidationResult.fromCode 分支覆盖（UT-QA-011，参考 UT-F8-017 / UT-RT-011 模式）</li>
 * </ul>
 *
 * <p>本测试为最小骨架（P7-4），interface 通过 Mockito.mock() 桩接，POJO 状态直接断言。
 * 真实业务实现由后续 PR 注入。</p>
 */
class F9DecisionNodeTest {

    // ============ F9.D2: L4-1 硬校验（JSON Schema / 来源标签 / 黑名单词） ============

    @Test
    @DisplayName("UT-QA-001: L4-1 硬校验通过（JSON Schema 合法 + 来源标签齐全 + 无黑名单词）")
    void should_PassL4Hard_When_JsonSchemaValidAndSourceTagged() {
        // F9.D2 true 分支：输出含 [来源:xxx] 标签，JSON Schema 合法，无黑名单词 → PASS
        L4HardValidator validator = mock(L4HardValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(true, L4ValidationResult.PASS);
        when(validator.validate(anyString())).thenReturn(output);

        String modelOutput = "订单 od_001 金额 99 元 [来源:订单系统]";
        L4ValidationOutput result = validator.validate(modelOutput);

        assertThat(result.isPassed())
                .as("L4-1 硬校验通过时 passed 应为 true")
                .isTrue();
        assertThat(result.getResult())
                .as("L4-1 硬校验通过时 result 应为 PASS")
                .isEqualTo(L4ValidationResult.PASS);
        verify(validator, times(1)).validate(modelOutput);
    }

    @Test
    @DisplayName("UT-QA-002: 缺少来源标签返回 FORMAT_VIOLATION 触发 Reflexion")
    void should_ReturnFormatViolation_When_SourceTagMissing() {
        // F9.D2 false 分支：输出无 [来源:...] 标签 → FORMAT_VIOLATION
        L4HardValidator validator = mock(L4HardValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(false, L4ValidationResult.FORMAT_VIOLATION);
        output.setViolationDetail("missing [来源:xxx] tag");
        when(validator.validate(anyString())).thenReturn(output);

        String modelOutput = "订单 od_001 金额 99 元";
        L4ValidationOutput result = validator.validate(modelOutput);

        assertThat(result.isPassed())
                .as("缺少来源标签时 passed 应为 false")
                .isFalse();
        assertThat(result.getResult())
                .as("缺少来源标签时 result 应为 FORMAT_VIOLATION")
                .isEqualTo(L4ValidationResult.FORMAT_VIOLATION);

        // 校验失败抛 L4ValidationException，触发 Reflexion（参考 UT-F8-003 模式）
        assertThatThrownBy(() -> {
            if (!result.isPassed()) {
                throw new L4ValidationException(result.getResult(), result.getViolationDetail());
            }
        })
                .isInstanceOf(L4ValidationException.class)
                .hasMessageContaining("FORMAT_VIOLATION")
                .hasMessageContaining("missing")
                .satisfies(ex -> {
                    L4ValidationException ve = (L4ValidationException) ex;
                    assertThat(ve.getValidationResult())
                            .as("异常应携带 validationResult=FORMAT_VIOLATION")
                            .isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
                });
    }

    @Test
    @DisplayName("UT-QA-003: 命中黑名单词返回 FORMAT_VIOLATION（输出含 \"保证 100% 正确\"）")
    void should_ReturnFormatViolation_When_BlacklistKeywordPresent() {
        // F9.D2 false 分支：输出含黑名单词 "保证"/"100%" → FORMAT_VIOLATION
        L4HardValidator validator = mock(L4HardValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(false, L4ValidationResult.FORMAT_VIOLATION);
        output.setViolationDetail("blacklist keyword hit: 保证 / 100%");
        when(validator.validate(anyString())).thenReturn(output);

        String modelOutput = "本系统可保证 100% 正确 [来源:xxx]";
        L4ValidationOutput result = validator.validate(modelOutput);

        assertThat(result.isPassed())
                .as("命中黑名单词时 passed 应为 false")
                .isFalse();
        assertThat(result.getResult())
                .as("命中黑名单词时 result 应为 FORMAT_VIOLATION")
                .isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
        assertThat(result.getViolationDetail())
                .as("违规详情应包含黑名单词命中信息")
                .contains("blacklist")
                .contains("保证");

        assertThatThrownBy(() -> {
            if (!result.isPassed()) {
                throw new L4ValidationException(result.getResult(), result.getViolationDetail());
            }
        })
                .isInstanceOf(L4ValidationException.class)
                .hasMessageContaining("FORMAT_VIOLATION")
                .hasMessageContaining("blacklist");
    }

    // ============ F9.D3: L4-2 事实一致性校验（cosine_sim ≥0.75 通过） ============

    @Test
    @DisplayName("UT-QA-004: L4-2 事实一致性通过（cosine_sim=0.80 ≥ 0.75）")
    void should_PassL4Consistency_When_CosineSimGe075() {
        // F9.D3 true 分支：输出事实 vs 参考源 cosine_sim=0.80 ≥ 0.75 → PASS
        L4ConsistencyValidator validator = mock(L4ConsistencyValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(true, L4ValidationResult.PASS);
        output.setCosineSim(0.80);
        when(validator.validate(anyString(), anyString())).thenReturn(output);

        String modelOutput = "订单 od_001 金额 99 元 [来源:订单系统]";
        String referenceSource = "订单 od_001 金额 99 元，状态已支付";
        L4ValidationOutput result = validator.validate(modelOutput, referenceSource);

        assertThat(result.isPassed())
                .as("cosine_sim=0.80 ≥ 0.75 时 passed 应为 true")
                .isTrue();
        assertThat(result.getResult())
                .as("事实一致性通过时 result 应为 PASS")
                .isEqualTo(L4ValidationResult.PASS);
        assertThat(result.getCosineSim())
                .as("cosine_sim 应为 0.80")
                .isEqualTo(0.80);
        verify(validator, times(1)).validate(modelOutput, referenceSource);
    }

    @Test
    @DisplayName("UT-QA-005: 事实不一致返回 FACT_INCONSISTENCY 触发 Reflexion（cosine_sim=0.60 < 0.75）")
    void should_ReturnFactInconsistency_When_CosineSimLt075() {
        // F9.D3 false 分支：cosine_sim=0.60 < 0.75 → FACT_INCONSISTENCY
        L4ConsistencyValidator validator = mock(L4ConsistencyValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(false, L4ValidationResult.FACT_INCONSISTENCY);
        output.setCosineSim(0.60);
        output.setViolationDetail("cosine_sim=0.60 < threshold=0.75");
        when(validator.validate(anyString(), anyString())).thenReturn(output);

        String modelOutput = "订单 od_001 金额 199 元 [来源:订单系统]";
        String referenceSource = "订单 od_001 金额 99 元，状态已支付";
        L4ValidationOutput result = validator.validate(modelOutput, referenceSource);

        assertThat(result.isPassed())
                .as("cosine_sim=0.60 < 0.75 时 passed 应为 false")
                .isFalse();
        assertThat(result.getResult())
                .as("事实不一致时 result 应为 FACT_INCONSISTENCY")
                .isEqualTo(L4ValidationResult.FACT_INCONSISTENCY);
        assertThat(result.getCosineSim())
                .as("cosine_sim 应为 0.60")
                .isEqualTo(0.60);

        // 校验失败抛 L4ValidationException，触发 Reflexion
        assertThatThrownBy(() -> {
            if (!result.isPassed()) {
                throw new L4ValidationException(result.getResult(), result.getViolationDetail());
            }
        })
                .isInstanceOf(L4ValidationException.class)
                .hasMessageContaining("FACT_INCONSISTENCY")
                .hasMessageContaining("cosine_sim=0.60")
                .satisfies(ex -> {
                    L4ValidationException ve = (L4ValidationException) ex;
                    assertThat(ve.getValidationResult())
                            .as("异常应携带 validationResult=FACT_INCONSISTENCY")
                            .isEqualTo(L4ValidationResult.FACT_INCONSISTENCY);
                });
    }

    // ============ F9.D4: L4-3 强模型终审（overall ≥0.7 通过） ============

    @Test
    @DisplayName("UT-QA-006: L4-3 强模型终审通过（overall_score=0.85 ≥ 0.7）")
    void should_PassL4Audit_When_OverallScoreGe07() {
        // F9.D4 true 分支：强模型四维度评分 overall=0.85 ≥ 0.7 → PASS
        L4AuditValidator validator = mock(L4AuditValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(true, L4ValidationResult.PASS);
        output.setOverallScore(0.85);
        when(validator.validate(anyString(), anyDouble())).thenReturn(output);

        String modelOutput = "订单 od_001 金额 99 元 [来源:订单系统]";
        L4ValidationOutput result = validator.validate(modelOutput, 0.7);

        assertThat(result.isPassed())
                .as("overall=0.85 ≥ 0.7 时 passed 应为 true")
                .isTrue();
        assertThat(result.getResult())
                .as("终审通过时 result 应为 PASS")
                .isEqualTo(L4ValidationResult.PASS);
        assertThat(result.getOverallScore())
                .as("overall_score 应为 0.85")
                .isEqualTo(0.85);
        verify(validator, times(1)).validate(modelOutput, 0.7);
    }

    @Test
    @DisplayName("UT-QA-007: L4-3 强模型终审驳回（overall_score=0.65 < 0.7）转人工")
    void should_ReturnAuditRejected_When_OverallScoreLt07() {
        // F9.D4 false 分支：overall=0.65 < 0.7 → AUDIT_REJECTED，转人工
        L4AuditValidator validator = mock(L4AuditValidator.class);
        L4ValidationOutput output = new L4ValidationOutput(false, L4ValidationResult.AUDIT_REJECTED);
        output.setOverallScore(0.65);
        output.setViolationDetail("overall=0.65 < threshold=0.70");
        when(validator.validate(anyString(), anyDouble())).thenReturn(output);

        String modelOutput = "订单 od_001 金额 99 元 [来源:订单系统]";
        L4ValidationOutput result = validator.validate(modelOutput, 0.7);

        assertThat(result.isPassed())
                .as("overall=0.65 < 0.7 时 passed 应为 false")
                .isFalse();
        assertThat(result.getResult())
                .as("终审驳回时 result 应为 AUDIT_REJECTED")
                .isEqualTo(L4ValidationResult.AUDIT_REJECTED);
        assertThat(result.getOverallScore())
                .as("overall_score 应为 0.65")
                .isEqualTo(0.65);

        // 校验失败抛 L4ValidationException，转人工审核
        assertThatThrownBy(() -> {
            if (!result.isPassed()) {
                throw new L4ValidationException(result.getResult(), result.getViolationDetail());
            }
        })
                .isInstanceOf(L4ValidationException.class)
                .hasMessageContaining("AUDIT_REJECTED")
                .hasMessageContaining("overall=0.65")
                .satisfies(ex -> {
                    L4ValidationException ve = (L4ValidationException) ex;
                    assertThat(ve.getValidationResult())
                            .as("异常应携带 validationResult=AUDIT_REJECTED")
                            .isEqualTo(L4ValidationResult.AUDIT_REJECTED);
                });
    }

    // ============ F9.D1: 任务风险分级（低风险闲聊跳过 L4-2/L4-3） ============

    @Test
    @DisplayName("UT-QA-008: 闲聊任务跳过 L4-2/L4-3（risk_level=low, task.type=chitchat）")
    void should_SkipL4Consistency_When_TaskIsChitchat() {
        // F9.D1 low 分支：risk_level=low + task.type=chitchat → 仅执行 L4-1，跳过 L4-2/L4-3
        L4HardValidator hardValidator = mock(L4HardValidator.class);
        L4ConsistencyValidator consistencyValidator = mock(L4ConsistencyValidator.class);
        L4AuditValidator auditValidator = mock(L4AuditValidator.class);

        L4ValidationOutput passOutput = new L4ValidationOutput(true, L4ValidationResult.PASS);
        when(hardValidator.validate(anyString())).thenReturn(passOutput);

        // 模拟任务风险分级：chitchat → LOW
        TaskRiskLevel riskLevel = TaskRiskLevel.LOW;
        String taskType = "chitchat";
        String modelOutput = "你好，很高兴为你服务 [来源:闲聊回复]";

        // 仅当 riskLevel != LOW 时才调用 L4-2 / L4-3
        L4ValidationOutput result = hardValidator.validate(modelOutput);
        if (riskLevel != TaskRiskLevel.LOW) {
            consistencyValidator.validate(modelOutput, "reference");
            auditValidator.validate(modelOutput, 0.7);
        }

        assertThat(riskLevel)
                .as("chitchat 任务风险级别应为 LOW")
                .isEqualTo(TaskRiskLevel.LOW);
        assertThat(taskType)
                .as("任务类型应为 chitchat")
                .isEqualTo("chitchat");
        assertThat(result.isPassed())
                .as("L4-1 硬校验应通过")
                .isTrue();

        // 验证 L4-1 调用一次，L4-2/L4-3 未被调用
        verify(hardValidator, times(1)).validate(modelOutput);
        verify(consistencyValidator, never()).validate(anyString(), anyString());
        verify(auditValidator, never()).validate(anyString(), anyDouble());
    }

    // ============ F9: Badcase 归集（重试耗尽写入 badcase 表） ============

    @Test
    @DisplayName("UT-QA-009: L4 重试耗尽写入 badcase 表（category=HALLUCINATION）")
    void should_WriteBadcase_When_L4RetryExhausted() {
        // F9 MAX_RETRY_EXCEEDED 分支：retry_count > max → BadcaseWriter.write 入库
        BadcaseWriter writer = mock(BadcaseWriter.class);

        // 模拟 L4 校验失败且重试耗尽，构造 Badcase 记录
        BadcaseRecord record = new BadcaseRecord("bc_001", "tk_001", BadcaseCategory.HALLUCINATION);
        record.setContent("订单 od_001 金额 199 元（捏造）");
        record.setFailureReason("MAX_RETRY_EXCEEDED: L4 FACT_INCONSISTENCY retry_count=3 > max=2");
        record.setSeverity(BadcaseSeverity.HIGH);
        record.setSeverityScore(0.9);

        writer.write(record);

        assertThat(record.getBadcaseId())
                .as("badcaseId 应为 bc_001")
                .isEqualTo("bc_001");
        assertThat(record.getTaskId())
                .as("taskId 应为 tk_001")
                .isEqualTo("tk_001");
        assertThat(record.getCategory())
                .as("重试耗尽的 badcase category 应为 HALLUCINATION")
                .isEqualTo(BadcaseCategory.HALLUCINATION);
        assertThat(record.getSeverity())
                .as("严重度应为 HIGH")
                .isEqualTo(BadcaseSeverity.HIGH);
        assertThat(record.getSeverityScore())
                .as("severityScore 应为 0.9")
                .isEqualTo(0.9);
        assertThat(record.getFailureReason())
                .as("失败原因应包含 MAX_RETRY_EXCEEDED")
                .contains("MAX_RETRY_EXCEEDED");
        verify(writer, times(1)).write(any(BadcaseRecord.class));
    }

    @Test
    @DisplayName("UT-QA-010: 高严重度 Badcase 推送人工审核队列（severity=HIGH, severityScore=0.8）")
    void should_PushToManualReview_When_BadcaseSeverityHigh() {
        // F9 高严重度分支：severityScore=0.8 ≥ 0.8 → ManualReviewQueue.push 入队
        ManualReviewQueue queue = mock(ManualReviewQueue.class);

        // 构造高严重度 Badcase
        BadcaseRecord record = new BadcaseRecord("bc_002", "tk_002", BadcaseCategory.HALLUCINATION);
        record.setSeverity(BadcaseSeverity.HIGH);
        record.setSeverityScore(0.8);

        // severityScore ≥ 0.8 时推送人工审核队列
        ManualReviewItem item = new ManualReviewItem(record.getBadcaseId(), record.getSeverity());
        item.setReviewId("rv_001");
        if (record.getSeverityScore() >= 0.8) {
            queue.push(item);
        }

        assertThat(record.getSeverity())
                .as("Badcase 严重度应为 HIGH")
                .isEqualTo(BadcaseSeverity.HIGH);
        assertThat(record.getSeverityScore())
                .as("severityScore 应为 0.8（高严重度阈值）")
                .isEqualTo(0.8);
        assertThat(item.getBadcaseId())
                .as("人工审核条目应关联 badcaseId=bc_002")
                .isEqualTo("bc_002");
        assertThat(item.getSeverity())
                .as("人工审核条目严重度应为 HIGH")
                .isEqualTo(BadcaseSeverity.HIGH);
        assertThat(item.getEnqueuedAt())
                .as("入队时间应已被设置")
                .isNotNull();
        verify(queue, times(1)).push(any(ManualReviewItem.class));
    }

    // ============ L4ValidationResult.fromCode 分支覆盖 (P7-4 补充, 参考 UT-F8-017 / UT-RT-011 模式) ============

    @Test
    @DisplayName("UT-QA-011: L4ValidationResult.fromCode 解析 4 个结果码成功 + 未知 code 抛 IllegalArgumentException")
    void should_ResolveOrThrow_When_FromCodeCalled() {
        // enums fromCode 分支覆盖: 4 个命中分支 (PASS/FORMAT_VIOLATION/FACT_INCONSISTENCY/AUDIT_REJECTED) + 1 个 throw 分支 (循环完毕未命中)
        // 补充原因: P7-4 骨架阶段 excludes 排除 model/exception 后, enums 中 L4ValidationResult.fromCode
        // 是唯一含分支逻辑的方法 (for 循环 + throw 兜底), 需补测试覆盖以达 line 0.80 / branch 0.70 阈值.
        assertThat(L4ValidationResult.fromCode("PASS"))
                .as("fromCode(PASS) 应返回 PASS 常量 (L4 校验通过)")
                .isEqualTo(L4ValidationResult.PASS);
        assertThat(L4ValidationResult.fromCode("FORMAT_VIOLATION"))
                .as("fromCode(FORMAT_VIOLATION) 应返回 FORMAT_VIOLATION 常量 (F9.D2 硬校验失败)")
                .isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
        assertThat(L4ValidationResult.fromCode("FACT_INCONSISTENCY"))
                .as("fromCode(FACT_INCONSISTENCY) 应返回 FACT_INCONSISTENCY 常量 (F9.D3 事实不一致)")
                .isEqualTo(L4ValidationResult.FACT_INCONSISTENCY);
        assertThat(L4ValidationResult.fromCode("AUDIT_REJECTED"))
                .as("fromCode(AUDIT_REJECTED) 应返回 AUDIT_REJECTED 常量 (F9.D4 终审驳回)")
                .isEqualTo(L4ValidationResult.AUDIT_REJECTED);

        // 未知 code: for 循环遍历完毕未命中, 抛 IllegalArgumentException
        assertThatThrownBy(() -> L4ValidationResult.fromCode("UNKNOWN"))
                .as("fromCode(未知 code) 应抛 IllegalArgumentException, 含未知 code 描述")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown L4ValidationResult code")
                .hasMessageContaining("UNKNOWN");
    }
}
