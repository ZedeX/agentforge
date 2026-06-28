package com.agent.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AuditLogService 默认实现：将审计事件以结构化 JSON 写入 SLF4J INFO 日志。
 *
 * <p>不落库，仅用于开发/测试阶段；生产可替换为 Kafka/DB 实现。</p>
 */
@Component
public class Slf4jAuditLogService implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void record(String tenantId, String userId, String action, String errorCode, String detail) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tenantId", tenantId);
        entry.put("userId", userId);
        entry.put("action", action);
        entry.put("errorCode", errorCode);
        entry.put("detail", detail);
        entry.put("timestamp", System.currentTimeMillis());

        try {
            log.info(objectMapper.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            // fallback：JSON 序列化失败也不丢失审计主线
            log.warn("AUDIT serialization failed action={} errorCode={} detail={}", action, errorCode, detail);
        }
    }
}
