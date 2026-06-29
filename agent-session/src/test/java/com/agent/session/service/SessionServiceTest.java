package com.agent.session.service;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;
import com.agent.session.repository.MessageRepository;
import com.agent.session.repository.SessionRepository;
import com.agent.session.testinfra.fixture.SessionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SessionService} 纯 Mockito 单元测试。
 *
 * <p>不依赖 Spring Context / Docker / Testcontainers，仅用 {@link org.mockito.Mockito#mock(Class)}
 * mock 三个依赖（SessionRepository / MessageRepository / ShortTermMemoryService），
 * 覆盖 createSession / getSession / closeSession / archiveSession / sendMessage / listMessages
 * 的正常路径与边界场景。</p>
 *
 * <p>P6-3/4/5：方法名统一为 {@code should_Xxx_When_Yyy}；JUnit 断言替换为 AssertJ；补充中文 @DisplayName。</p>
 */
class SessionServiceTest {

    private SessionRepository sessionRepository;
    private MessageRepository messageRepository;
    private ShortTermMemoryService memoryService;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        messageRepository = mock(MessageRepository.class);
        memoryService = mock(ShortTermMemoryService.class);
        sessionService = new SessionService(sessionRepository, messageRepository, memoryService);

        // save 默认返回入参本身（便于 createSession 使用 saved.getSessionId()）
        when(sessionRepository.save(any(Session.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== createSession ====================

    @Test
    @DisplayName("createSession 应返回 active 状态的 Session 并写入仓储与短期记忆")
    void should_ReturnSessionWithActiveStatus_When_CreateSession() {
        Session result = sessionService.createSession(1001L, "u_001", 2001L, "测试会话");

        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isNotNull();
        assertThat(result.getSessionId()).startsWith("ss_");
        assertThat(result.getStatus()).isEqualTo(SessionStatus.ACTIVE.getCode());
        assertThat(result.getTokenUsed()).isEqualTo(0L);
        assertThat(result.getUserId()).isEqualTo("u_001");
        assertThat(result.getAgentId()).isEqualTo(2001L);

        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(memoryService, times(1))
                .saveContext(eq(result.getSessionId()), any(ShortTermMemoryService.SessionContext.class));
    }

    @Test
    @DisplayName("title 为 null 时 createSession 应使用空字符串填充默认上下文")
    void should_UseDefaultContentTypeWhenNull_When_CreateSessionWithNullTitle() {
        Session result = sessionService.createSession(1001L, "u_001", 2001L, null);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isNull();

        ArgumentCaptor<ShortTermMemoryService.SessionContext> ctxCaptor =
                ArgumentCaptor.forClass(ShortTermMemoryService.SessionContext.class);
        verify(memoryService).saveContext(eq(result.getSessionId()), ctxCaptor.capture());
        ShortTermMemoryService.SessionContext ctx = ctxCaptor.getValue();
        assertThat(ctx.getTaskGoal()).isNull();
        assertThat(ctx.getSystemPrompt()).isEqualTo("");
        assertThat(ctx.getRecalledMemory()).isEqualTo("");
        assertThat(ctx.getRecentMessages()).isNotNull();
        assertThat(ctx.getToolHistory()).isNotNull();
    }

    // ==================== getSession ====================

    @Test
    @DisplayName("getSession 在仓储命中时应返回对应 Session")
    void should_ReturnSession_When_SessionExists() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        Optional<Session> result = sessionService.getSession("ss_001");

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(session);
    }

    @Test
    @DisplayName("getSession 在仓储未命中时应返回 empty")
    void should_ReturnEmpty_When_SessionNotExists() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        Optional<Session> result = sessionService.getSession("ss_notexist");

        assertThat(result).isEmpty();
    }

    // ==================== closeSession ====================

    @Test
    @DisplayName("closeSession 命中时应将状态置为 CLOSED 并清理短期记忆")
    void should_ReturnTrueAndSetClosedStatus_When_CloseExistingSession() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        boolean result = sessionService.closeSession("ss_001");

        assertThat(result).isTrue();

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.CLOSED.getCode());

        verify(memoryService).clearContext("ss_001");
    }

    @Test
    @DisplayName("closeSession 在仓储未命中时应返回 false 且不触发任何写操作")
    void should_ReturnFalse_When_CloseMissingSession() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        boolean result = sessionService.closeSession("ss_notexist");

        assertThat(result).isFalse();
        verify(sessionRepository, never()).save(any(Session.class));
        verify(memoryService, never()).clearContext(anyString());
    }

    // ==================== archiveSession ====================

    @Test
    @DisplayName("archiveSession 命中时应将状态置为 ARCHIVED 并清理短期记忆")
    void should_ReturnTrueAndSetArchivedStatus_When_ArchiveExistingSession() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        boolean result = sessionService.archiveSession("ss_001");

        assertThat(result).isTrue();

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.ARCHIVED.getCode());

        verify(memoryService).clearContext("ss_001");
    }

    @Test
    @DisplayName("archiveSession 在仓储未命中时应返回 false 且不触发任何写操作")
    void should_ReturnFalse_When_ArchiveMissingSession() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        boolean result = sessionService.archiveSession("ss_notexist");

        assertThat(result).isFalse();
        verify(sessionRepository, never()).save(any(Session.class));
        verify(memoryService, never()).clearContext(anyString());
    }

    // ==================== sendMessage ====================

    @Test
    @DisplayName("向 active 会话发消息应返回 assistant 回复并写入仓储与短期记忆")
    void should_ReturnAssistantMessage_When_SessionActive() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        Message result = sessionService.sendMessage("ss_001", "hello", "text", "u_001");

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("[echo] hello");
        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContentType()).isEqualTo("text");
        assertThat(result.getMsgId()).isNotNull();

        verify(messageRepository, times(2)).save(any(Message.class));
        verify(memoryService, times(2)).appendMessage(eq("ss_001"), anyMap());
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    @DisplayName("会话不存在时 sendMessage 应抛 IllegalArgumentException")
    void should_ThrowIllegalArgument_When_SessionNotFound() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.sendMessage("ss_notexist", "hello", "text", "u_001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ss_notexist");

        verify(messageRepository, never()).save(any(Message.class));
        verify(memoryService, never()).appendMessage(anyString(), anyMap());
    }

    @Test
    @DisplayName("会话状态为 CLOSED 时 sendMessage 应抛 IllegalStateException")
    void should_ThrowIllegalState_When_SessionClosed() {
        Session session = SessionFixtures.aSession("ss_001");
        session.setStatus(SessionStatus.CLOSED.getCode());
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.sendMessage("ss_001", "hello", "text", "u_001"))
                .isInstanceOf(IllegalStateException.class);

        verify(messageRepository, never()).save(any(Message.class));
        verify(memoryService, never()).appendMessage(anyString(), anyMap());
    }

    @Test
    @DisplayName("会话状态为 ARCHIVED 时 sendMessage 应抛 IllegalStateException")
    void should_ThrowIllegalState_When_SessionArchived() {
        Session session = SessionFixtures.aSession("ss_001");
        session.setStatus(SessionStatus.ARCHIVED.getCode());
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.sendMessage("ss_001", "hello", "text", "u_001"))
                .isInstanceOf(IllegalStateException.class);

        verify(messageRepository, never()).save(any(Message.class));
        verify(memoryService, never()).appendMessage(anyString(), anyMap());
    }

    @Test
    @DisplayName("contentType 为 null 时 sendMessage 应使用默认值 text")
    void should_UseDefaultContentType_When_ContentTypeIsNull() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        sessionService.sendMessage("ss_001", "hello", null, "u_001");

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(2)).save(msgCaptor.capture());
        Message savedUserMsg = msgCaptor.getAllValues().get(0);
        assertThat(savedUserMsg.getRole()).isEqualTo(MessageRole.USER);
        assertThat(savedUserMsg.getContentType()).isEqualTo("text");
    }

    // ==================== listMessages ====================

    @Test
    @DisplayName("listMessages 应返回分页结果")
    void should_ReturnPage_When_ListMessages() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Message> messages = List.of(
                SessionFixtures.aMessage("ss_001", MessageRole.USER, "hi"),
                SessionFixtures.aMessage("ss_001", MessageRole.ASSISTANT, "[echo] hi")
        );
        Page<Message> expectedPage = new PageImpl<>(messages, pageable, messages.size());
        when(messageRepository.findBySessionId("ss_001", pageable)).thenReturn(expectedPage);

        Page<Message> result = sessionService.listMessages("ss_001", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getContent()).isEqualTo(messages);
        verify(messageRepository).findBySessionId("ss_001", pageable);
    }
}
