package com.agent.session.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionTest {

    @Test
    void shouldSetAndGetAllFields() {
        Session s = new Session();
        s.setSessionId("ss_abc123");
        s.setTenantId(1001L);
        s.setUserId("u_001");
        s.setAgentId(2001L);
        s.setTitle("测试会话");
        s.setStatus(SessionStatus.ACTIVE.getCode());
        s.setTokenUsed(1500L);
        s.setContextSummary("摘要内容");

        assertEquals("ss_abc123", s.getSessionId());
        assertEquals(1001L, s.getTenantId());
        assertEquals("u_001", s.getUserId());
        assertEquals(2001L, s.getAgentId());
        assertEquals("测试会话", s.getTitle());
        assertEquals(1, s.getStatus());
        assertEquals(1500L, s.getTokenUsed());
        assertEquals("摘要内容", s.getContextSummary());
    }

    @Test
    void shouldConvertStatusBetweenCodeAndEnum() {
        assertEquals(SessionStatus.ACTIVE, SessionStatus.fromCode(1));
        assertEquals(SessionStatus.CLOSED, SessionStatus.fromCode(3));
        assertEquals("active", SessionStatus.ACTIVE.getApiValue());
        assertEquals("closed", SessionStatus.CLOSED.getApiValue());
    }

    @Test
    void shouldInitMessageDefaults() {
        Message m = new Message();
        m.setMsgId("msg_001");
        m.setSessionId("ss_abc");
        m.setRole(MessageRole.USER);
        m.setContent("hello");
        m.prePersist();

        assertEquals("msg_001", m.getMsgId());
        assertEquals(MessageRole.USER, m.getRole());
        assertEquals("text", m.getContentType());
        assertEquals(0, m.getTokenCount());
        assertEquals(false, m.getIsCompressed());
        assertNotNull(m.getCreatedAt());
        assertNotNull(m.getUpdatedAt());
    }
}
