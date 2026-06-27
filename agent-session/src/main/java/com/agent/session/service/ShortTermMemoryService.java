package com.agent.session.service;

import com.agent.session.config.ShortTermMemoryProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 短期记忆服务（ADR-002：状态外置 Redis）。
 *
 * Redis Key 设计：
 *   sm:{sessionId}:ctx   类型：Hash
 *   字段：
 *     - systemPrompt      系统提示词
 *     - taskGoal          任务目标
 *     - recentMessages    最近 N 轮对话（JSON List）
 *     - toolHistory       工具调用历史（JSON List）
 *     - recalledMemory    召回记忆片段
 *
 * TTL：24h（与 doc 一致）
 *
 * 数据规模：recentMessages 限制为 max-recent-messages（默认 20），
 *          超出后滚动剔除最早一条。
 */
@Service
public class ShortTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemoryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final ShortTermMemoryProperties properties;

    public ShortTermMemoryService(StringRedisTemplate redisTemplate,
                                  ShortTermMemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void saveContext(String sessionId, SessionContext ctx) {
        String key = buildKey(sessionId);
        try {
            redisTemplate.opsForHash().put(key, "systemPrompt", nullSafe(ctx.getSystemPrompt()));
            redisTemplate.opsForHash().put(key, "taskGoal", nullSafe(ctx.getTaskGoal()));
            redisTemplate.opsForHash().put(key, "recentMessages",
                    MAPPER.writeValueAsString(ctx.getRecentMessages() == null ? List.of() : ctx.getRecentMessages()));
            redisTemplate.opsForHash().put(key, "toolHistory",
                    MAPPER.writeValueAsString(ctx.getToolHistory() == null ? List.of() : ctx.getToolHistory()));
            redisTemplate.opsForHash().put(key, "recalledMemory", nullSafe(ctx.getRecalledMemory()));
            redisTemplate.expire(key, computeTtl());
        } catch (Exception e) {
            log.error("saveContext failed session={}", sessionId, e);
            throw new IllegalStateException("save context failed", e);
        }
    }

    public SessionContext loadContext(String sessionId) {
        String key = buildKey(sessionId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }
        SessionContext ctx = new SessionContext();
        ctx.setSystemPrompt((String) entries.get("systemPrompt"));
        ctx.setTaskGoal((String) entries.get("taskGoal"));
        ctx.setRecentMessages(parseList((String) entries.get("recentMessages")));
        ctx.setToolHistory(parseList((String) entries.get("toolHistory")));
        ctx.setRecalledMemory((String) entries.get("recalledMemory"));
        return ctx;
    }

    public void appendMessage(String sessionId, Map<String, Object> message) {
        String key = buildKey(sessionId);
        try {
            String raw = (String) redisTemplate.opsForHash().get(key, "recentMessages");
            List<Map<String, Object>> list = parseList(raw);
            list = new ArrayList<>(list);
            list.add(message);

            int max = properties.getMaxRecentMessages();
            while (list.size() > max) {
                list.remove(0);
            }

            redisTemplate.opsForHash().put(key, "recentMessages", MAPPER.writeValueAsString(list));
            redisTemplate.expire(key, computeTtl());
        } catch (Exception e) {
            log.error("appendMessage failed session={}", sessionId, e);
            throw new IllegalStateException("append message failed", e);
        }
    }

    public void clearContext(String sessionId) {
        redisTemplate.delete(buildKey(sessionId));
    }

    protected Duration computeTtl() {
        if (properties.getTtlHours() <= 0) {
            return Duration.ofHours(24);
        }
        return Duration.ofHours(properties.getTtlHours());
    }

    private String buildKey(String sessionId) {
        return properties.getKeyPrefix() + ":" + sessionId + ":ctx";
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("parseList failed json={}", json, e);
            return new ArrayList<>();
        }
    }

    public static class SessionContext {
        private String systemPrompt;
        private String taskGoal;
        private List<Map<String, Object>> recentMessages;
        private List<Map<String, Object>> toolHistory;
        private String recalledMemory;

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getTaskGoal() {
            return taskGoal;
        }

        public void setTaskGoal(String taskGoal) {
            this.taskGoal = taskGoal;
        }

        public List<Map<String, Object>> getRecentMessages() {
            return recentMessages;
        }

        public void setRecentMessages(List<Map<String, Object>> recentMessages) {
            this.recentMessages = recentMessages;
        }

        public List<Map<String, Object>> getToolHistory() {
            return toolHistory;
        }

        public void setToolHistory(List<Map<String, Object>> toolHistory) {
            this.toolHistory = toolHistory;
        }

        public String getRecalledMemory() {
            return recalledMemory;
        }

        public void setRecalledMemory(String recalledMemory) {
            this.recalledMemory = recalledMemory;
        }
    }
}
