package com.agent.drift.api.impl;

import com.agent.drift.api.BaselineAnchor;
import com.agent.drift.model.BehaviorBaseline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 1 行为基线锚定实现 (F11 L1: 首次运行锚定行为基线, 后续运行据此比对)。
 *
 * <p>简单实现策略：使用 ConcurrentHashMap 按 agentId 持久化基线快照。
 * 同一 agentId 已存在基线时返回 false (不覆盖), 否则写入并返回 true。</p>
 */
@Component
public class BaselineAnchorImpl implements BaselineAnchor {

    private static final Logger log = LoggerFactory.getLogger(BaselineAnchorImpl.class);

    private final Map<String, BehaviorBaseline> baselines = new ConcurrentHashMap<>();

    @Override
    public boolean anchor(BehaviorBaseline baseline) {
        if (baseline == null || baseline.getAgentId() == null) {
            log.warn("L1 基线锚定收到空 baseline 或空 agentId, 跳过");
            return false;
        }
        String agentId = baseline.getAgentId();
        BehaviorBaseline previous = baselines.putIfAbsent(agentId, baseline);
        if (previous != null) {
            log.debug("L1 基线已存在: agentId={}, 跳过覆盖", agentId);
            return false;
        }
        log.info("L1 基线锚定成功: agentId={}, version={}", agentId, baseline.getVersion());
        return true;
    }

    /** 读取已锚定基线 (供测试 / 漂移检测使用)。 */
    public BehaviorBaseline getBaseline(String agentId) {
        return baselines.get(agentId);
    }
}