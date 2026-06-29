package com.agent.quality.api;

import com.agent.quality.model.L4ValidationOutput;

/**
 * L4-1 规则化硬校验器 (doc 11-detail-flow F9.D2, PRD §三(一)2).
 *
 * <p>硬校验项：
 * <ul>
 *   <li>JSON Schema 合法性</li>
 *   <li>来源标签 [来源:xxx] 必填</li>
 *   <li>黑名单词命中检测（如 "绝对"、"100%"、"保证"）</li>
 * </ul>
 * 任一失败返回 FORMAT_VIOLATION，触发 Reflexion.</p>
 */
public interface L4HardValidator {

    /**
     * 对模型输出执行 L4-1 硬校验.
     *
     * @param output 模型生成输出
     * @return 校验输出（passed=true 时 result=PASS；passed=false 时 result=FORMAT_VIOLATION）
     */
    L4ValidationOutput validate(String output);
}
