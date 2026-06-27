package com.agent.gateway.client;

import com.agent.gateway.dto.SafetyCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * risk-control 客户端：调用 risk-control.PreCheck gRPC 接口。
 *
 * 当前实现为本地降级 stub：直接返回 PASS。
 * 待 agent-proto 中 risk_control.proto 定义发布后，
 * 替换为 @GrpcClient("risk-control") 注入的 stub 调用即可。
 */
@Component
public class RiskControlClient {

    private static final Logger log = LoggerFactory.getLogger(RiskControlClient.class);

    public SafetyCheckResult preCheck(String tenantId, String userId, String content) {
        log.debug("risk-control preCheck stub tenant={} userId={} contentLen={}",
                tenantId, userId, content == null ? 0 : content.length());
        return new SafetyCheckResult("PASS", null, null);
    }
}
