package com.agent.session.service;

import com.agent.session.config.SseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SSE 推送服务（ADR-002 状态外置 Redis）。
 *
 * 链路：
 *   服务端 -> SsePushService.publish(sessionId, event, data)
 *          -> Redis Pub/Sub channel: session:{sessionId}:events
 *          -> 监听器收到消息后通过 SseEmitter.send 推送客户端
 *
 * 客户端：GET /api/v1/sessions/{sessionId}/stream 返回 SseEmitter
 */
@Service
public class SsePushService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SsePushService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final SseProperties properties;

    private final ConcurrentMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private RedisMessageListenerContainer listenerContainer;

    public SsePushService(StringRedisTemplate redisTemplate, SseProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        // 容器实例由 Spring Data Redis 自动配置注入；此处使用懒加载
        // 实际订阅在 registerEmitter 时按 channel 进行
    }

    @PreDestroy
    public void destroy() {
        emitters.values().forEach(em -> {
            try {
                em.complete();
            } catch (Exception ignore) {
            }
        });
        emitters.clear();
    }

    /**
     * 注册 SSE 连接，返回 emitter 并订阅 Redis channel。
     */
    public SseEmitter register(String sessionId) {
        SseEmitter emitter = new SseEmitter(properties.getTimeoutMs());
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("SSE error session={}", sessionId, ex);
            emitters.remove(sessionId);
        });

        // 发送初始 connected 事件
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("sessionId", sessionId)));
        } catch (IOException e) {
            log.warn("send connected event failed session={}", sessionId, e);
        }

        log.info("SSE register session={}", sessionId);
        return emitter;
    }

    /**
     * 推送事件到 Redis channel（供其他实例订阅）。
     */
    public void publish(String sessionId, String event, Map<String, Object> data) {
        String channel = buildChannel(sessionId);
        try {
            String payload = MAPPER.writeValueAsString(Map.of(
                    "event", event,
                    "data", data
            ));
            redisTemplate.convertAndSend(channel, payload);
            log.debug("publish session={} channel={} event={}", sessionId, channel, event);
        } catch (Exception e) {
            log.error("publish failed session={} event={}", sessionId, event, e);
            throw new IllegalStateException("publish event failed", e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = MAPPER.readValue(body, Map.class);
            String sessionId = extractSessionId(message.getChannel());
            SseEmitter emitter = emitters.get(sessionId);
            if (emitter == null) {
                return;
            }
            String event = (String) payload.get("event");
            Object data = payload.get("data");
            emitter.send(SseEmitter.event().name(event).data(data));
            log.debug("onMessage session={} event={}", sessionId, event);
        } catch (Exception e) {
            log.error("onMessage failed body={}", body, e);
        }
    }

    private String extractSessionId(byte[] channelBytes) {
        String channel = new String(channelBytes, java.nio.charset.StandardCharsets.UTF_8);
        String prefix = properties.getChannelPrefix() + ":";
        String suffix = ":events";
        if (channel.startsWith(prefix) && channel.endsWith(suffix)) {
            return channel.substring(prefix.length(), channel.length() - suffix.length());
        }
        return channel;
    }

    private String buildChannel(String sessionId) {
        return properties.getChannelPrefix() + ":" + sessionId + ":events";
    }
}
