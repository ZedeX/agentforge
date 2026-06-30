package com.agent.drift.api.impl;

import com.agent.drift.api.DriftCorrector;
import com.agent.drift.enums.DriftLevel;
import com.agent.drift.model.DriftCorrectAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 3 漂移纠正器实现 (F11 L3: 会话级注入核心约束 / 系统级回滚)。
 *
 * <p>简单实现策略：</p>
 * <ul>
 *   <li>SESSION: 注入核心约束摘要 (记录待注入摘要)；</li>
 *   <li>SYSTEM: 回滚至 targetVersion (记录目标版本)；</li>
 *   <li>action 为空或缺少 agentId / level → 返回 false。</li>
 * </ul>
 */
@Component
public class DriftCorrectorImpl implements DriftCorrector {

    private static final Logger log = LoggerFactory.getLogger(DriftCorrectorImpl.class);

    /** 会话级待注入约束摘要快照: agentId -> 摘要文本。 */
    private final Map<String, String> sessionInjections = new ConcurrentHashMap<>();
    /** 系统级回滚目标版本快照: agentId -> targetVersion。 */
    private final Map<String, String> systemRollbacks = new ConcurrentHashMap<>();

    @Override
    public boolean correct(DriftCorrectAction action) {
        if (action == null || action.getLevel() == null || action.getAgentId() == null) {
            log.warn("L3 纠正器收到空 action / 缺少 level / 缺少 agentId, 跳过");
            return false;
        }
        DriftLevel level = action.getLevel();
        String agentId = action.getAgentId();
        switch (level) {
            case SESSION:
                sessionInjections.put(agentId, action.getCoreConstraintSummary());
                log.info("L3 会话级纠正: agentId={}, 注入核心约束摘要", agentId);
                return true;
            case SYSTEM:
                systemRollbacks.put(agentId, action.getTargetVersion());
                log.warn("L3 系统级纠正: agentId={}, 回滚至 version={}", agentId, action.getTargetVersion());
                return true;
            default:
                log.warn("L3 未知纠正级别: {}, 跳过", level);
                return false;
        }
    }

    public String getSessionInjection(String agentId) {
        return sessionInjections.get(agentId);
    }

    public String getSystemRollbackTarget(String agentId) {
        return systemRollbacks.get(agentId);
    }
}