package com.agent.gateway.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * session-service REST 客户端（代理调用）。
 * 当前为本地降级 stub：直接返回响应 ID，待 session-service 上线后启用 RestClient。
 */
@Component
public class SessionServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceClient.class);

    @Value("${session-service.base-url:http://localhost:8082}")
    private String baseUrl;

    public String sendChat(String sessionId, String content) {
        String responseId = "ss_chat_resp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("sendChat stub session={} contentLen={} -> respId={}", sessionId, content.length(), responseId);
        return responseId;
    }
}
