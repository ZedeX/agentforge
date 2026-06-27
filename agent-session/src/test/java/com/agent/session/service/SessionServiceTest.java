package com.agent.session.service;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;
import com.agent.session.repository.MessageRepository;
import com.agent.session.repository.SessionRepository;
import com.agent.session.testinfra.fixture.SessionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void createSession_shouldReturnSessionWithActiveStatus() {
        Session result = sessionService.createSession(1001L, "u_001", 2001L, "测试会话");

        assertNotNull(result);
        assertNotNull(result.getSessionId());
        assertTrue(result.getSessionId().startsWith("ss_"),
                "sessionId should start with ss_ prefix");
        assertEquals(SessionStatus.ACTIVE.getCode(), result.getStatus());
        assertEquals(0L, result.getTokenUsed());
        assertEquals("u_001", result.getUserId());
        assertEquals(2001L, result.getAgentId());

        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(memoryService, times(1))
                .saveContext(eq(result.getSessionId()), any(ShortTermMemoryService.SessionContext.class));
    }

    @Test
    void createSession_shouldUseDefaultContentTypeWhenNull() {
        Session result = sessionService.createSession(1001L, "u_001", 2001L, null);

        assertNotNull(result);
        assertNull(result.getTitle());

        ArgumentCaptor<ShortTermMemoryService.SessionContext> ctxCaptor =
                ArgumentCaptor.forClass(ShortTermMemoryService.SessionContext.class);
        verify(memoryService).saveContext(eq(result.getSessionId()), ctxCaptor.capture());
        ShortTermMemoryService.SessionContext ctx = ctxCaptor.getValue();
        assertNull(ctx.getTaskGoal());
        assertEquals("", ctx.getSystemPrompt());
        assertEquals("", ctx.getRecalledMemory());
        assertNotNull(ctx.getRecentMessages());
        assertNotNull(ctx.getToolHistory());
    }

    // ==================== getSession ====================

    @Test
    void getSession_shouldReturnSessionWhenExists() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        Optional<Session> result = sessionService.getSession("ss_001");

        assertTrue(result.isPresent());
        assertSame(session, result.get());
    }

    @Test
    void getSession_shouldReturnEmptyWhenNotExists() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        Optional<Session> result = sessionService.getSession("ss_notexist");

        assertTrue(result.isEmpty());
    }

    // ==================== closeSession ====================

    @Test
    void closeSession_shouldReturnTrueAndSetClosedStatus() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        boolean result = sessionService.closeSession("ss_001");

        assertTrue(result);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.CLOSED.getCode(), captor.getValue().getStatus());

        verify(memoryService).clearContext("ss_001");
    }

    @Test
    void closeSession_shouldReturnFalseWhenNotExists() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        boolean result = sessionService.closeSession("ss_notexist");

        assertFalse(result);
        verify(sessionRepository, never()).save(any(Session.class));
        verify(memoryService, never()).clearContext(anyString());
    }

    // ==================== archiveSession ====================

    @Test
    void archiveSession_shouldReturnTrueAndSetArchivedStatus() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        boolean result = sessionService.archiveSession("ss_001");

        assertTrue(result);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.ARCHIVED.getCode(), captor.getValue().getStatus());

        verify(memoryService).clearContext("ss_001");
    }

    @Test
    void archiveSession_shouldReturnFalseWhenNotExists() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        boolean result = sessionService.archiveSession("ss_notexist");

        assertFalse(result);
        verify(sessionRepository, never()).save(any(Session.class));
        verify(memoryService, never()).clearContext(anyString());
    }

    // ==================== sendMessage ====================

    @Test
    void sendMessage_shouldReturnAssistantMessage_whenSessionActive() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        Message result = sessionService.sendMessage("ss_001", "hello", "text", "u_001");

        assertNotNull(result);
        assertEquals("[echo] hello", result.getContent());
        assertEquals(MessageRole.ASSISTANT, result.getRole());
        assertEquals("text", result.getContentType());
        assertNotNull(result.getMsgId());

        verify(messageRepository, times(2)).save(any(Message.class));
        verify(memoryService, times(2)).appendMessage(eq("ss_001"), anyMap());
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void sendMessage_shouldThrowIllegalArgument_whenSessionNotFound() {
        when(sessionRepository.findBySessionId("ss_notexist")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sessionService.sendMessage("ss_notexist", "hello", "text", "u_001"));

        assertTrue(ex.getMessage().contains("ss_notexist"));
        verify(messageRepository, never()).save(any(Message.class));
        verify(memoryService, never()).appendMessage(anyString(), anyMap());
    }

    @Test
    void sendMessage_shouldThrowIllegalState_whenSessionClosed() {
        Session session = SessionFixtures.aSession("ss_001");
        session.setStatus(SessionStatus.CLOSED.getCode());
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> sessionService.sendMessage("ss_001", "hello", "text", "u_001"));

        verify(messageRepository, never()).save(any(Message.class));
        verify(memoryService, never()).appendMessage(anyString(), anyMap());
    }

    @Test
    void sendMessage_shouldThrowIllegalState_whenSessionArchived() {
        Session session = SessionFixtures.aSession("ss_001");
        session.setStatus(SessionStatus.ARCHIVED.getCode());
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> sessionService.sendMessage("ss_001", "hello", "text", "u_001"));

        verify(messageRepository, never()).save(any(Message.class));
        verify(memoryService, never()).appendMessage(anyString(), anyMap());
    }

    @Test
    void sendMessage_shouldUseDefaultContentType_whenContentTypeIsNull() {
        Session session = SessionFixtures.aSession("ss_001");
        when(sessionRepository.findBySessionId("ss_001")).thenReturn(Optional.of(session));

        sessionService.sendMessage("ss_001", "hello", null, "u_001");

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(2)).save(msgCaptor.capture());
        Message savedUserMsg = msgCaptor.getAllValues().get(0);
        assertEquals(MessageRole.USER, savedUserMsg.getRole());
        assertEquals("text", savedUserMsg.getContentType());
    }

    // ==================== listMessages ====================

    @Test
    void listMessages_shouldReturnPage() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Message> messages = List.of(
                SessionFixtures.aMessage("ss_001", MessageRole.USER, "hi"),
                SessionFixtures.aMessage("ss_001", MessageRole.ASSISTANT, "[echo] hi")
        );
        Page<Message> expectedPage = new PageImpl<>(messages, pageable, messages.size());
        when(messageRepository.findBySessionId("ss_001", pageable)).thenReturn(expectedPage);

        Page<Message> result = sessionService.listMessages("ss_001", pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(messages, result.getContent());
        verify(messageRepository).findBySessionId("ss_001", pageable);
    }
}
