package com.agent.session.service;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;
import com.agent.session.repository.MessageRepository;
import com.agent.session.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话生命周期服务。
 *
 * 方法：
 *   - createSession   创建会话
 *   - getSession       查询会话
 *   - closeSession     关闭会话
 *   - archiveSession   归档会话
 *   - sendMessage      发送消息（写入短期记忆 + 持久化 + 调用 task-orchestrator）
 *   - listMessages     历史消息分页
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ShortTermMemoryService memoryService;

    public SessionService(SessionRepository sessionRepository,
                         MessageRepository messageRepository,
                         ShortTermMemoryService memoryService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.memoryService = memoryService;
    }

    @Transactional
    public Session createSession(Long tenantId, String userId, Long agentId, String title) {
        Session s = new Session();
        s.setSessionId("ss_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        s.setTenantId(tenantId);
        s.setUserId(userId);
        s.setAgentId(agentId);
        s.setTitle(title);
        s.setStatus(SessionStatus.ACTIVE.getCode());
        s.setTokenUsed(0L);

        Session saved = sessionRepository.save(s);

        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setSystemPrompt("");
        ctx.setTaskGoal(title);
        ctx.setRecentMessages(java.util.List.of());
        ctx.setToolHistory(java.util.List.of());
        ctx.setRecalledMemory("");
        memoryService.saveContext(saved.getSessionId(), ctx);

        log.info("createSession sessionId={} tenant={} user={}", saved.getSessionId(), tenantId, userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Session> getSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }

    @Transactional
    public boolean closeSession(String sessionId) {
        Optional<Session> opt = sessionRepository.findBySessionId(sessionId);
        if (opt.isEmpty()) {
            return false;
        }
        Session s = opt.get();
        s.setStatus(SessionStatus.CLOSED.getCode());
        sessionRepository.save(s);
        memoryService.clearContext(sessionId);
        log.info("closeSession sessionId={}", sessionId);
        return true;
    }

    @Transactional
    public boolean archiveSession(String sessionId) {
        Optional<Session> opt = sessionRepository.findBySessionId(sessionId);
        if (opt.isEmpty()) {
            return false;
        }
        Session s = opt.get();
        s.setStatus(SessionStatus.ARCHIVED.getCode());
        sessionRepository.save(s);
        memoryService.clearContext(sessionId);
        log.info("archiveSession sessionId={}", sessionId);
        return true;
    }

    @Transactional
    public Message sendMessage(String sessionId, String content, String contentType, String userId) {
        Optional<Session> opt = sessionRepository.findBySessionId(sessionId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        Session session = opt.get();
        if (SessionStatus.CLOSED.getCode() == session.getStatus()
                || SessionStatus.ARCHIVED.getCode() == session.getStatus()) {
            throw new IllegalStateException("Session is closed: " + sessionId);
        }

        // 1. 持久化 user 消息
        Message userMsg = new Message();
        userMsg.setMsgId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        userMsg.setSessionId(sessionId);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent(content);
        userMsg.setContentType(contentType == null ? "text" : contentType);
        userMsg.setTokenCount(estimateTokens(content));
        messageRepository.save(userMsg);

        // 2. 写入短期记忆
        memoryService.appendMessage(sessionId, java.util.Map.of(
                "role", "user",
                "content", content,
                "msgId", userMsg.getMsgId()
        ));

        // 3. 调用 task-orchestrator（占位：当前为同步 stub，待 orchestrator gRPC 上线后替换）
        // 实际实现：String taskId = orchestratorClient.submitTask(...)
        Message assistantMsg = new Message();
        assistantMsg.setMsgId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole(MessageRole.ASSISTANT);
        assistantMsg.setContent("[echo] " + content);
        assistantMsg.setContentType("text");
        assistantMsg.setTokenCount(estimateTokens(assistantMsg.getContent()));
        messageRepository.save(assistantMsg);

        memoryService.appendMessage(sessionId, java.util.Map.of(
                "role", "assistant",
                "content", assistantMsg.getContent(),
                "msgId", assistantMsg.getMsgId()
        ));

        // 4. 更新会话 last_msg_at + token
        session.setLastMsgAt(Instant.now());
        session.setTokenUsed(session.getTokenUsed() + userMsg.getTokenCount() + assistantMsg.getTokenCount());
        sessionRepository.save(session);

        log.info("sendMessage session={} userMsg={} assistantMsg={}",
                sessionId, userMsg.getMsgId(), assistantMsg.getMsgId());
        return assistantMsg;
    }

    @Transactional(readOnly = true)
    public Page<Message> listMessages(String sessionId, Pageable pageable) {
        return messageRepository.findBySessionId(sessionId, pageable);
    }

    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        // 简化估算：中英文混合 1 字符约 0.6 token（与 agent-common.TokenEstimator 对齐）
        return (int) Math.ceil(text.length() * 0.6);
    }
}
