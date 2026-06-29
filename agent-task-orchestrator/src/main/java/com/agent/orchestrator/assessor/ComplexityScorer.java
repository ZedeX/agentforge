package com.agent.orchestrator.assessor;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * 复杂度评分器（doc 03-task-engine §2.2）。
 *
 * <p>接收 {@link ComplexityDimensions}（6 维度评分），按规则返回 {@link ComplexityLevel}。</p>
 *
 * <p>判级规则（以 docs/tests/unit-test-cases.md §6 UT-PLAN-001~004 为准）：</p>
 * <ol>
 *   <li>总分 ≤ 8 → L1</li>
 *   <li>总分 9~14 → L2</li>
 *   <li>总分 &gt; 14 → L3</li>
 *   <li>风险维度 = 3 时强制升级 L3（即使总分对应 L1/L2）</li>
 *   <li>执行维度 = 3 且 风险维度 = 3 时强制 L3（已被规则 4 覆盖，保留语义清晰）</li>
 * </ol>
 *
 * <p>注：设计文档 §2.2 原始阈值（加权 ≤4 / 5~9 / ≥10）与 unit-test-cases.md
 * （无加权 ≤8 / 9~14 / &gt;14）存在差异；本实现以 unit-test-cases.md 为准，
 * 使用 {@link ComplexityDimensions#totalScore()} 直接求和。</p>
 *
 * <p>本类仅负责"分数 → 等级"映射，不负责"goal 文本 → 分数"打分
 * （后者由 RuleFilter / ModelAssessor 承担）。</p>
 */
public class ComplexityScorer {

    /** L1 上界阈值：总分 ≤ {@value} 判级 L1。 */
    public static final int L1_MAX_SCORE = 8;
    /** L2 上界阈值：总分 ≤ {@value} 判级 L2（与 L1_MAX_SCORE+1 构成 [9, 14] 区间）。 */
    public static final int L2_MAX_SCORE = 14;
    /** 风险维度高分阈值：risk ≥ {@value} 触发强制升级 L3。 */
    public static final int RISK_HIGH_THRESHOLD = 3;
    /** 执行维度高分阈值：execution ≥ {@value} 配合 risk=3 触发强制 L3。 */
    public static final int EXECUTION_HIGH_THRESHOLD = 3;

    /**
     * 对六维度评分应用判级规则，返回复杂度等级。
     *
     * @param dimensions 六维度评分（不可为 null，各维度需在 0~3 范围内）
     * @return 复杂度等级 L1/L2/L3
     * @throws BusinessException 当 dimensions 为 null 或某维度超出 0~3 范围时，
     *                           错误码为 {@link ErrorCode#PARAM_INVALID}
     */
    public ComplexityLevel score(ComplexityDimensions dimensions) {
        validate(dimensions);

        int totalScore = dimensions.totalScore();
        int risk = dimensions.getRisk();
        int execution = dimensions.getExecution();

        // 规则 4/5：风险维度=3 强制 L3（覆盖执行维度=3 且 风险维度=3 的情况）
        if (risk >= RISK_HIGH_THRESHOLD) {
            return ComplexityLevel.L3;
        }

        // 规则 1~3：按总分阈值判级
        if (totalScore <= L1_MAX_SCORE) {
            return ComplexityLevel.L1;
        }
        if (totalScore <= L2_MAX_SCORE) {
            return ComplexityLevel.L2;
        }
        return ComplexityLevel.L3;
    }

    /**
     * 校验入参合法性：非空 + 各维度 0~3 范围。
     *
     * @param dimensions 待校验评分
     * @throws BusinessException 当 dimensions 为 null 或某维度超出 0~3 范围
     */
    private void validate(ComplexityDimensions dimensions) {
        if (dimensions == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "ComplexityDimensions 不能为 null");
        }
        validateRange("goal", dimensions.getGoal());
        validateRange("execution", dimensions.getExecution());
        validateRange("domain", dimensions.getDomain());
        validateRange("knowledge", dimensions.getKnowledge());
        validateRange("risk", dimensions.getRisk());
        validateRange("context", dimensions.getContext());
    }

    /**
     * 校验单维度取值在 [0, 3] 范围内。
     *
     * @param name  维度名称（用于错误信息）
     * @param value 维度值
     * @throws BusinessException 当 value &lt; 0 或 &gt; 3
     */
    private void validateRange(String name, int value) {
        if (value < 0 || value > 3) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "ComplexityDimensions." + name + "=" + value + " 超出合法范围 [0, 3]");
        }
    }
}
