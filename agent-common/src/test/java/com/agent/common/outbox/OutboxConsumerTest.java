package com.agent.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD Red: Failing tests for {@link OutboxConsumer} with idempotent consumption.
 *
 * <p>Uses {@link ConsumeLogRepository} (simulating event_consume_log pattern from S-03)
 * to ensure same message is processed only once.</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxConsumerTest {

    @Mock
    private ConsumeLogRepository consumeLogRepository;

    @Mock
    private OutboxMessageHandler messageHandler;

    private OutboxConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OutboxConsumer(consumeLogRepository, messageHandler);
    }

    @Test
    @DisplayName("consumer should process message and record in consume log")
    void should_ProcessAndRecord_When_NewMessage() {
        String eventId = "outbox-123";
        String topic = "tool.audit";
        String payload = "{\"toolId\":\"search\"}";
        when(consumeLogRepository.existsById(eventId)).thenReturn(false);
        when(consumeLogRepository.save(anyString())).thenReturn(new ConsumeLog(eventId, Instant.now()));

        boolean result = consumer.consume(eventId, topic, payload);

        assertThat(result).isTrue();
        verify(messageHandler).handle(topic, payload);
        verify(consumeLogRepository).save(eventId);
    }

    @Test
    @DisplayName("consumer should skip duplicate message (idempotent)")
    void should_SkipDuplicate_When_MessageAlreadyConsumed() {
        String eventId = "outbox-123";
        when(consumeLogRepository.existsById(eventId)).thenReturn(true);

        boolean result = consumer.consume(eventId, "tool.audit", "{\"toolId\":\"search\"}");

        assertThat(result).isFalse();
        verify(messageHandler, never()).handle(anyString(), anyString());
        verify(consumeLogRepository, never()).save(anyString());
    }

    @Test
    @DisplayName("consumer should handle 3 deliveries of same message with only 1 side-effect")
    void should_BeIdempotent_When_SameMessageDeliveredThreeTimes() {
        String eventId = "outbox-456";
        String topic = "runtime.stepstate";
        String payload = "{\"step\":5,\"phase\":\"OBSERVE\"}";

        // First delivery: not consumed yet
        when(consumeLogRepository.existsById(eventId)).thenReturn(false);
        when(consumeLogRepository.save(anyString())).thenReturn(new ConsumeLog(eventId, Instant.now()));

        boolean result1 = consumer.consume(eventId, topic, payload);
        assertThat(result1).isTrue();
        verify(messageHandler, times(1)).handle(topic, payload);

        // Second and third delivery: already consumed
        when(consumeLogRepository.existsById(eventId)).thenReturn(true);
        boolean result2 = consumer.consume(eventId, topic, payload);
        boolean result3 = consumer.consume(eventId, topic, payload);

        assertThat(result2).isFalse();
        assertThat(result3).isFalse();
        // Handler still only called once total
        verify(messageHandler, times(1)).handle(anyString(), anyString());
    }

    @Test
    @DisplayName("consumer should return false when handler throws exception")
    void should_ReturnFalse_When_HandlerThrows() {
        String eventId = "outbox-789";
        when(consumeLogRepository.existsById(eventId)).thenReturn(false);
        doThrow(new RuntimeException("handler failure")).when(messageHandler).handle(anyString(), anyString());

        boolean result = consumer.consume(eventId, "tool.audit", "{}");

        assertThat(result).isFalse();
    }
}
