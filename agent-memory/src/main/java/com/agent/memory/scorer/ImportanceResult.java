package com.agent.memory.scorer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 重要性评分结果（Plan 03 T7 / doc 04-memory §4.3）.
 *
 * <p>包含最终 score（[0,1]）、level（HIGH/MEDIUM/LOW）和各维度得分。
 *
 * <p>等级阈值（doc 04 §4.3）：
 * <ul>
 *   <li>score ≥ 0.7 → HIGH</li>
 *   <li>0.4 ≤ score &lt; 0.7 → MEDIUM</li>
 *   <li>score &lt; 0.4 → LOW</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class ImportanceResult {

    /** 最终得分 [0,1]。 */
    private double score;
    /** 等级 HIGH/MEDIUM/LOW。 */
    private String level;
    /** 各维度得分明细。 */
    private ImportanceDimensions dimensions;

    public ImportanceResult(double score, String level, ImportanceDimensions dimensions) {
        this.score = score;
        this.level = level;
        this.dimensions = dimensions;
    }

    /** 按阈值分级（doc 04 §4.3）。 */
    public static String classify(double score) {
        if (score >= 0.7) {
            return "HIGH";
        } else if (score < 0.4) {
            return "LOW";
        } else {
            return "MEDIUM";
        }
    }
}
