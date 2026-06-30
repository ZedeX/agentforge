package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.RiskClassifier;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ToolMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * F8 风险分级实现 (R1/R2/R3 分类)。
 *
 * <p>分类规则 (doc 08 §2):
 * <ul>
 *   <li>R1: executor=general, side_effect=none (低风险, 本地直接执行)</li>
 *   <li>R2: executor=proxy, side_effect=reversible (中风险, 代理转发)</li>
 *   <li>R3: executor=sandbox 或 side_effect=irreversible (高风险, 沙箱 + 双审批)</li>
 *   <li>其他未命中标准组合: 兜底按 R2 处理 (中风险, 保守)</li>
 * </ul>
 * null meta 兜底为 R3 (安全优先)。</p>
 */
@Component
public class RiskClassifierImpl implements RiskClassifier {

    private static final Logger log = LoggerFactory.getLogger(RiskClassifierImpl.class);

    @Override
    public ToolRiskLevel classify(ToolMeta meta) {
        if (meta == null) {
            log.warn("风险分级收到空 meta, 默认 R3 (高风险, 安全兜底)");
            return ToolRiskLevel.R3;
        }
        ExecutorType exec = meta.getExecutorType();
        SideEffect side = meta.getSideEffect();

        // R3 判定优先: sandbox 执行器 或 不可逆副作用
        if (exec == ExecutorType.SANDBOX || side == SideEffect.IRREVERSIBLE) {
            log.debug("工具 [{}] 分类 R3 (exec={}, side={})", meta.getToolId(), exec, side);
            return ToolRiskLevel.R3;
        }
        // R1: general + none
        if (exec == ExecutorType.GENERAL && side == SideEffect.NONE) {
            log.debug("工具 [{}] 分类 R1 (general + none)", meta.getToolId());
            return ToolRiskLevel.R1;
        }
        // R2: proxy + reversible
        if (exec == ExecutorType.PROXY && side == SideEffect.REVERSIBLE) {
            log.debug("工具 [{}] 分类 R2 (proxy + reversible)", meta.getToolId());
            return ToolRiskLevel.R2;
        }
        // 兜底: 未命中标准组合按 R2 (中风险, 保守)
        log.debug("工具 [{}] 组合 (exec={}, side={}) 未命中标准规则, 兜底 R2",
                meta.getToolId(), exec, side);
        return ToolRiskLevel.R2;
    }
}
