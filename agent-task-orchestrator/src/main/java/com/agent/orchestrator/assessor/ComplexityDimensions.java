package com.agent.orchestrator.assessor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 六维度复杂度评分 POJO（doc 03-task-engine §2.2）。
 *
 * <p>每维度取值 0~3，总分 0~18（无加权，6 维度直接相加）。</p>
 *
 * <table border="1">
 *   <caption>六维度定义</caption>
 *   <tr><th>维度</th><th>0 分</th><th>3 分</th></tr>
 *   <tr><td>goal（目标）</td><td>单一明确</td><td>多目标跨域</td></tr>
 *   <tr><td>execution（执行）</td><td>无工具</td><td>多步且含写操作</td></tr>
 *   <tr><td>domain（领域）</td><td>通用闲聊</td><td>跨领域专业</td></tr>
 *   <tr><td>knowledge（知识）</td><td>无需外部知识</td><td>需多源交叉验证</td></tr>
 *   <tr><td>risk（风险）</td><td>无副作用</td><td>含 R3 高危写/不可逆</td></tr>
 *   <tr><td>context（上下文）</td><td>单轮即解</td><td>需多模态/长文档</td></tr>
 * </table>
 *
 * <p>判级阈值（以 docs/tests/unit-test-cases.md §6 UT-PLAN-001~004 为准）：</p>
 * <ul>
 *   <li>总分 ≤ 8 → L1</li>
 *   <li>总分 9~14 → L2</li>
 *   <li>总分 &gt; 14 → L3</li>
 *   <li>risk = 3 时强制升级 L3</li>
 * </ul>
 *
 * <p>注：设计文档 §2.2 原始阈值（加权 ≤4 / 5~9 / ≥10）与 unit-test-cases.md
 * （无加权 ≤8 / 9~14 / &gt;14）存在差异；本实现以 unit-test-cases.md 为准，
 * {@link #totalScore()} 直接返回 6 维度之和。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplexityDimensions {

    /** 目标维度（0~3）。 */
    private int goal;
    /** 执行维度（0~3）。 */
    private int execution;
    /** 领域维度（0~3）。 */
    private int domain;
    /** 知识维度（0~3）。 */
    private int knowledge;
    /** 风险维度（0~3），=3 表示含 R3 高危写操作。 */
    private int risk;
    /** 上下文维度（0~3）。 */
    private int context;

    /**
     * 六维度总分（无加权，直接相加，取值 0~18）。
     *
     * @return 6 维度之和
     */
    public int totalScore() {
        return goal + execution + domain + knowledge + risk + context;
    }
}
