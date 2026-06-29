package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.AssessResponse;
import com.agent.orchestrator.assessor.ComplexityLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 复杂度评估结果 → {@link AssessResponse} proto 映射器。
 *
 * <p>承担 {@code ComplexityLevel} → proto {@code complexity} 数值（1=L1 / 2=L2 / 3=L3）
 * 的转换职责，避免 gRPC 服务层直接散落枚举到数值的映射逻辑。</p>
 *
 * <p>映射规则（对齐 planning.proto {@code AssessResponse.complexity} 字段注释）：</p>
 * <ul>
 *   <li>{@link ComplexityLevel#L1} → 1（简单任务：单 Agent 直出）</li>
 *   <li>{@link ComplexityLevel#L2} → 2（中等任务：少量工具调用）</li>
 *   <li>{@link ComplexityLevel#L3} → 3（复杂任务：多 Agent 协同）</li>
 * </ul>
 *
 * <p>设计说明：本类为无状态纯函数式映射器，{@code @Component} 注解便于 Spring 注入。
 * 不依赖 {@link ComplexityLevel#ordinal()}（避免枚举顺序变动引发数值漂移），
 * 显式 switch 映射保证可读性与可维护性。</p>
 */
@Component
public class AssessResultMapper {

    /**
     * 把 {@link ComplexityLevel} 等级 + reason + 建议能力标签组装为 {@link AssessResponse}。
     *
     * @param level         复杂度等级（不可为 null）
     * @param reason        评估原因说明（用于审计/调试，可为空字符串）
     * @param suggestedTags 建议能力标签列表（可为 null，将替换为空列表）
     * @return 构造完成的 {@link AssessResponse}
     * @throws IllegalArgumentException 当 level 为 null
     */
    public AssessResponse toAssessResponse(ComplexityLevel level,
                                            String reason,
                                            List<String> suggestedTags) {
        if (level == null) {
            throw new IllegalArgumentException("ComplexityLevel 不可为 null");
        }
        List<String> tags = suggestedTags == null ? List.of() : suggestedTags;
        return AssessResponse.newBuilder()
                .setComplexity(toNumeric(level))
                .setReason(reason == null ? "" : reason)
                .addAllSuggestedAbilityTags(tags)
                .build();
    }

    /**
     * {@link ComplexityLevel} → 数值（L1=1 / L2=2 / L3=3）。
     *
     * <p>对外暴露为 public 便于测试与上层复用（如直接取数值写日志）。</p>
     *
     * @param level 复杂度等级
     * @return 1 / 2 / 3
     */
    public int toNumeric(ComplexityLevel level) {
        switch (level) {
            case L1:
                return 1;
            case L2:
                return 2;
            case L3:
                return 3;
            default:
                throw new IllegalArgumentException("未知 ComplexityLevel: " + level);
        }
    }
}
