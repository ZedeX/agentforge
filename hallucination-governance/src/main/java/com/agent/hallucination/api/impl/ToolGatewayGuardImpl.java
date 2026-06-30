package com.agent.hallucination.api.impl;

import com.agent.hallucination.api.ToolGatewayGuard;
import com.agent.hallucination.enums.GuardResult;
import com.agent.hallucination.model.ToolCallGuardRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Layer 5 工具网关守卫实现 (F10 L5: 工具参数 schema 预校验)。
 */
@Component
public class ToolGatewayGuardImpl implements ToolGatewayGuard {

    private static final Logger log = LoggerFactory.getLogger(ToolGatewayGuardImpl.class);

    @Override
    public GuardResult guard(ToolCallGuardRequest request) {
        if (request == null) {
            log.warn("L5 工具网关守卫收到空请求, 拒绝放行");
            return GuardResult.REJECTED;
        }
        Map<String, Object> params = request.getParams();
        if (params == null) {
            params = Map.of();
        }
        List<String> requiredFields = request.getRequiredFields();
        if (requiredFields == null || requiredFields.isEmpty()) {
            log.debug("L5 工具 [{}] 未声明必填字段, 放行", request.getToolId());
            return GuardResult.ALLOWED;
        }
        for (String field : requiredFields) {
            if (field == null) {
                continue;
            }
            Object value = params.get(field);
            if (Objects.isNull(value) || (value instanceof String s && s.isBlank())) {
                log.warn("L5 工具 [{}] 必填字段 [{}] 缺失或为空, 拒绝放行", request.getToolId(), field);
                return GuardResult.REJECTED;
            }
        }
        log.debug("L5 工具 [{}] 参数 schema 校验通过, 放行", request.getToolId());
        return GuardResult.ALLOWED;
    }
}
