package com.agent.session.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    @DisplayName("Session 实体应能正确设置和读取所有字段")
    void should_SetAndGetAllFields_When_SessionFieldsPopulated() {
        Session s = new Session();
        s.setSessionId("ss_abc123");
        s.setTenantId(1001L);
        s.setUserId("u_001");
        s.setAgentId(2001L);
        s.setTitle("测试会话");
        s.setStatus(SessionStatus.ACTIVE.getCode());
        s.setTokenUsed(1500L);
        s.setContextSummary("摘要内容");

        assertThat(s.getSessionId()).isEqualTo("ss_abc123");
        assertThat(s.getTenantId()).isEqualTo(1001L);
        assertThat(s.getUserId()).isEqualTo("u_001");
        assertThat(s.getAgentId()).isEqualTo(2001L);
        assertThat(s.getTitle()).isEqualTo("测试会话");
        assertThat(s.getStatus()).isEqualTo(1);
        assertThat(s.getTokenUsed()).isEqualTo(1500L);
        assertThat(s.getContextSummary()).isEqualTo("摘要内容");
    }

    @Test
    @DisplayName("SessionStatus 应支持在 code 与 enum 之间双向转换")
    void should_ConvertStatusBetweenCodeAndEnum_When_StatusAccessed() {
        assertThat(SessionStatus.fromCode(1)).isEqualTo(SessionStatus.ACTIVE);
        assertThat(SessionStatus.fromCode(3)).isEqualTo(SessionStatus.CLOSED);
        assertThat(SessionStatus.ACTIVE.getApiValue()).isEqualTo("active");
        assertThat(SessionStatus.CLOSED.getApiValue()).isEqualTo("closed");
    }

    @Test
    @DisplayName("Message 在 prePersist 后应初始化默认值")
    void should_InitMessageDefaults_When_PrePersist() {
        Message m = new Message();
        m.setMsgId("msg_001");
        m.setSessionId("ss_abc");
        m.setRole(MessageRole.USER);
        m.setContent("hello");
        m.prePersist();

        assertThat(m.getMsgId()).isEqualTo("msg_001");
        assertThat(m.getRole()).isEqualTo(MessageRole.USER);
        assertThat(m.getContentType()).isEqualTo("text");
        assertThat(m.getTokenCount()).isEqualTo(0);
        assertThat(m.getIsCompressed()).isEqualTo(false);
        assertThat(m.getCreatedAt()).isNotNull();
        assertThat(m.getUpdatedAt()).isNotNull();
    }
}
