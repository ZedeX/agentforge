package com.agent.quality.api;

import com.agent.quality.model.L4ValidationOutput;

/**
 * L4-2 事实一致性校验器 (doc 11-detail-flow F9.D3, PRD §三(一)2).
 *
 * <p>计算模型输出事实与参考源的向量相似度 cosine_sim，
 * 当 cosine_sim ≥ 0.75 通过；否则返回 FACT_INCONSISTENCY，触发 Reflexion.</p>
 */
public interface L4ConsistencyValidator {

    /**
     * 对模型输出执行 L4-2 事实一致性校验.
     *
     * @param output         模型生成输出
     * @param referenceSource 参考源文本（知识库 / 文档片段）
     * @return 校验输出（passed=true 时 result=PASS；passed=false 时 result=FACT_INCONSISTENCY）
     */
    L4ValidationOutput validate(String output, String referenceSource);
}
