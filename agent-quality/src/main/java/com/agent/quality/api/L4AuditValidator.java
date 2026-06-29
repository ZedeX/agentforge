package com.agent.quality.api;

import com.agent.quality.model.L4ValidationOutput;

/**
 * L4-3 强模型终审校验器 (doc 11-detail-flow F9.D4, PRD §三(一)2).
 *
 * <p>调用强模型对输出做四维度评分（事实性 / 完整性 / 一致性 / 安全性），
 * 当 overall ≥ overallScoreThreshold（默认 0.7）通过；否则返回 AUDIT_REJECTED，转人工.</p>
 */
public interface L4AuditValidator {

    /**
     * 对模型输出执行 L4-3 强模型终审.
     *
     * @param output                 模型生成输出
     * @param overallScoreThreshold  overall 评分阈值（默认 0.7）
     * @return 校验输出（passed=true 时 result=PASS；passed=false 时 result=AUDIT_REJECTED）
     */
    L4ValidationOutput validate(String output, double overallScoreThreshold);
}
