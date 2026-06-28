package com.agent.gateway.service;

/**
 * 审计日志服务接口：记录关键安全事件（如 payload 超限拒绝）。
 *
 * <p>当前实现为结构化 JSON SLF4J 日志，不落库；后续可替换为落库实现。</p>
 */
public interface AuditLogService {

    /**
     * 记录一条审计事件。
     *
     * @param tenantId  租户 ID（鉴权后注入，可能为 null）
     * @param userId    用户 ID（鉴权后注入，可能为 null）
     * @param action    事件动作（如 PAYLOAD_REJECTED）
     * @param errorCode 错误码（如 PAYLOAD_TOO_LARGE）
     * @param detail    详情（如被拒路径、body 大小等）
     */
    void record(String tenantId, String userId, String action, String errorCode, String detail);
}
