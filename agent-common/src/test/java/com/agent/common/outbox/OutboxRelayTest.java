package com.agent.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD Red: Failing tests for {@link OutboxRelay} and {@link OutboxPublisher}.
 *
 * <p>Tests the relay scheduling logic: poll PENDING → publish → markSent/markFailed.
 * Mocks the publisher and repository to isolate relay logic.</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxPublisher outboxPublisher;

    private OutboxProperties properties;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setBatchSize(100);
        properties.setMaxRetries(5);
        properties.setInitialRetryDelayMs(1000);
        properties.setEnabled(true);
        relay = new OutboxRelay(outboxRepository, outboxPublisher, properties);
    }

    // ==================== Successful publish ====================

    @Test
    @DisplayName("relay should poll PENDING messages and publish them")
    void should_PollAndPublish_When_PendingMessagesExist() {
        OutboxMessage msg1 = createMessage(1L, "agg-1", "tool.audit", OutboxStatus.PENDING);
        OutboxMessage msg2 = createMessage(2L, "agg-2", "runtime.stepstate", OutboxStatus.PENDING);
        when(outboxRepository.findPending(100)).thenReturn(List.of(msg1, msg2));
        when(outboxPublisher.publish(anyString(), anyString(), anyString())).thenReturn(true);

        relay.relayMessages();

        verify(outboxPublisher).publish("tool.audit", "agg-1", msg1.getPayload());
        verify(outboxPublisher).publish("runtime.stepstate", "agg-2", msg2.getPayload());
        verify(outboxRepository).markSent(1L);
        verify(outboxRepository).markSent(2L);
    }

    @Test
    @DisplayName("relay should do nothing when no PENDING messages")
    void should_DoNothing_When_NoPendingMessages() {
        when(outboxRepository.findPending(100)).thenReturn(List.of());

        relay.relayMessages();

        verify(outboxPublisher, never()).publish(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).markSent(anyLong());
        verify(outboxRepository, never()).markFailed(anyLong(), any(Instant.class));
    }

    // ==================== Publish failure ====================

    @Test
    @DisplayName("relay should mark FAILED on publish failure and set exponential backoff nextRetryAt")
    void should_MarkFailed_When_PublishFails() {
        OutboxMessage msg = createMessage(1L, "agg-1", "tool.audit", OutboxStatus.PENDING);
        msg.setRetryCount(0);
        when(outboxRepository.findPending(100)).thenReturn(List.of(msg));
        when(outboxPublisher.publish(anyString(), anyString(), anyString())).thenReturn(false);

        relay.relayMessages();

        verify(outboxRepository, never()).markSent(anyLong());
        ArgumentCaptor<Instant> nextRetryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).markFailed(anyLong(), nextRetryCaptor.capture());
        // Exponential backoff: 2^0 * 1000ms = 1000ms after now
        assertThat(nextRetryCaptor.getValue()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    @DisplayName("relay should mark DEAD when retryCount exceeds maxRetries")
    void should_MarkDead_When_RetryCountExceedsMax() {
        OutboxMessage msg = createMessage(1L, "agg-1", "tool.audit", OutboxStatus.PENDING);
        msg.setRetryCount(5); // equals maxRetries
        when(outboxRepository.findPending(100)).thenReturn(List.of(msg));
        when(outboxPublisher.publish(anyString(), anyString(), anyString())).thenReturn(false);

        relay.relayMessages();

        verify(outboxRepository).markDead(1L);
        verify(outboxRepository, never()).markFailed(anyLong(), any(Instant.class));
    }

    // ==================== Exponential backoff ====================

    @Test
    @DisplayName("relay should calculate exponential backoff: 2^retry * initialDelay")
    void should_CalculateExponentialBackoff_When_MarkingFailed() {
        OutboxMessage msg = createMessage(1L, "agg-1", "tool.audit", OutboxStatus.PENDING);
        msg.setRetryCount(2); // 2^2 * 1000 = 4000ms
        when(outboxRepository.findPending(100)).thenReturn(List.of(msg));
        when(outboxPublisher.publish(anyString(), anyString(), anyString())).thenReturn(false);

        Instant before = Instant.now();
        relay.relayMessages();
        Instant after = Instant.now().plusMillis(5000); // generous window

        ArgumentCaptor<Instant> nextRetryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).markFailed(anyLong(), nextRetryCaptor.capture());
        Instant nextRetry = nextRetryCaptor.getValue();
        // Should be approximately before + 2^2 * 1000ms = before + 4000ms
        assertThat(nextRetry).isAfter(before.plusMillis(3000));
        assertThat(nextRetry).isBefore(after);
    }

    // ==================== Disabled relay ====================

    @Test
    @DisplayName("relay should skip when disabled")
    void should_Skip_When_Disabled() {
        properties.setEnabled(false);

        relay.relayMessages();

        verify(outboxRepository, never()).findPending(anyInt());
    }

    // ==================== Helper ====================

    private OutboxMessage createMessage(Long id, String aggregateId, String topic, OutboxStatus status) {
        OutboxMessage msg = new OutboxMessage();
        msg.setId(id);
        msg.setAggregateId(aggregateId);
        msg.setTopic(topic);
        msg.setPayload("{\"test\":true}");
        msg.setStatus(status);
        msg.setRetryCount(0);
        msg.setNextRetryAt(Instant.now().minusSeconds(1));
        msg.setCreatedAt(Instant.now());
        return msg;
    }
}
