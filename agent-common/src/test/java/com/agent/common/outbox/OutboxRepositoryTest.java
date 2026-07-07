package com.agent.common.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for {@link OutboxMessage} entity and {@link OutboxRepository}.
 *
 * <p>Uses H2 in-memory DB with @DataJpaTest for slice test.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class OutboxRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OutboxRepository outboxRepository;

    // ==================== Entity field tests ====================

    @Test
    @DisplayName("OutboxMessage should persist all core fields and auto-generate id")
    void should_PersistAllFields_When_OutboxMessageSaved() {
        OutboxMessage msg = new OutboxMessage();
        msg.setAggregateId("tool-call-123");
        msg.setTopic("tool.audit");
        msg.setPayload("{\"toolId\":\"search\",\"status\":\"success\"}");
        msg.setStatus(OutboxStatus.PENDING);
        msg.setRetryCount(0);
        msg.setNextRetryAt(Instant.now().minusSeconds(1)); // past = due now
        msg.setCreatedAt(Instant.now());

        OutboxMessage saved = outboxRepository.saveAndFlush(msg);

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getAggregateId()).isEqualTo("tool-call-123");
        assertThat(saved.getTopic()).isEqualTo("tool.audit");
        assertThat(saved.getPayload()).contains("search");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getNextRetryAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ==================== findPending tests ====================

    @Test
    @DisplayName("findPending should return only PENDING messages ordered by nextRetryAt")
    void should_ReturnOnlyPending_When_FindPendingCalled() {
        Instant now = Instant.now();

        OutboxMessage pending1 = createMessage("agg-1", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(10));
        OutboxMessage pending2 = createMessage("agg-2", "topic.b", OutboxStatus.PENDING, 0, now.minusSeconds(5));
        OutboxMessage sent = createMessage("agg-3", "topic.c", OutboxStatus.SENT, 0, now.minusSeconds(1));
        outboxRepository.saveAllAndFlush(List.of(pending1, pending2, sent));

        List<OutboxMessage> result = outboxRepository.findPending(10);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OutboxMessage::getAggregateId)
                .containsExactly("agg-1", "agg-2");
    }

    @Test
    @DisplayName("findPending should respect the limit parameter")
    void should_RespectLimit_When_FindPendingCalled() {
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            outboxRepository.saveAndFlush(createMessage("agg-" + i, "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(i)));
        }

        List<OutboxMessage> result = outboxRepository.findPending(3);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("findPending should not return FAILED or DEAD messages")
    void should_ExcludeFailedAndDead_When_FindPendingCalled() {
        Instant now = Instant.now();

        outboxRepository.saveAndFlush(createMessage("agg-pending", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(5)));
        outboxRepository.saveAndFlush(createMessage("agg-failed", "topic.b", OutboxStatus.FAILED, 2, now.minusSeconds(10)));
        outboxRepository.saveAndFlush(createMessage("agg-dead", "topic.c", OutboxStatus.DEAD, 5, now.minusSeconds(1)));

        List<OutboxMessage> result = outboxRepository.findPending(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateId()).isEqualTo("agg-pending");
    }

    @Test
    @DisplayName("findPending should not return messages whose nextRetryAt is in the future")
    void should_ExcludeFutureRetry_When_FindPendingCalled() {
        Instant now = Instant.now();

        outboxRepository.saveAndFlush(createMessage("agg-due", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(5)));
        outboxRepository.saveAndFlush(createMessage("agg-future", "topic.b", OutboxStatus.PENDING, 0, now.plusSeconds(300)));

        List<OutboxMessage> result = outboxRepository.findPending(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateId()).isEqualTo("agg-due");
    }

    // ==================== Status transition tests ====================

    @Test
    @DisplayName("markSent should change PENDING to SENT and set sentAt")
    @Transactional
    void should_ChangeToSent_When_MarkSentCalled() {
        Instant now = Instant.now();
        OutboxMessage msg = outboxRepository.saveAndFlush(
                createMessage("agg-1", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(5)));

        outboxRepository.markSent(msg.getId());
        entityManager.flush();
        entityManager.clear();

        OutboxMessage updated = outboxRepository.findById(msg.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(updated.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed should increment retryCount and set nextRetryAt")
    @Transactional
    void should_IncrementRetryAndSetNextRetry_When_MarkFailedCalled() {
        Instant now = Instant.now();
        OutboxMessage msg = outboxRepository.saveAndFlush(
                createMessage("agg-1", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(5)));

        Instant nextRetry = now.plusSeconds(20);
        outboxRepository.markFailed(msg.getId(), nextRetry);
        entityManager.flush();
        entityManager.clear();

        OutboxMessage updated = outboxRepository.findById(msg.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getNextRetryAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(nextRetry.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("markDead should change FAILED to DEAD")
    @Transactional
    void should_ChangeToDead_When_MarkDeadCalled() {
        Instant now = Instant.now();
        OutboxMessage msg = outboxRepository.saveAndFlush(
                createMessage("agg-1", "topic.a", OutboxStatus.FAILED, 5, now.minusSeconds(5)));

        outboxRepository.markDead(msg.getId());
        entityManager.flush();
        entityManager.clear();

        OutboxMessage updated = outboxRepository.findById(msg.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    // ==================== countByStatus tests ====================

    @Test
    @DisplayName("countByStatus should return correct count per status")
    void should_ReturnCorrectCount_When_CountByStatusCalled() {
        Instant now = Instant.now();

        outboxRepository.saveAndFlush(createMessage("agg-1", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(5)));
        outboxRepository.saveAndFlush(createMessage("agg-2", "topic.b", OutboxStatus.PENDING, 0, now.minusSeconds(10)));
        outboxRepository.saveAndFlush(createMessage("agg-3", "topic.c", OutboxStatus.SENT, 0, now.minusSeconds(1)));
        outboxRepository.saveAndFlush(createMessage("agg-4", "topic.d", OutboxStatus.FAILED, 3, now.minusSeconds(1)));
        outboxRepository.saveAndFlush(createMessage("agg-5", "topic.e", OutboxStatus.DEAD, 5, now.minusSeconds(1)));

        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(2);
        assertThat(outboxRepository.countByStatus(OutboxStatus.SENT)).isEqualTo(1);
        assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
        assertThat(outboxRepository.countByStatus(OutboxStatus.DEAD)).isEqualTo(1);
    }

    // ==================== findDead tests ====================

    @Test
    @DisplayName("findDead should return only DEAD messages")
    void should_ReturnOnlyDead_When_FindDeadCalled() {
        Instant now = Instant.now();

        outboxRepository.saveAndFlush(createMessage("agg-1", "topic.a", OutboxStatus.PENDING, 0, now.minusSeconds(5)));
        outboxRepository.saveAndFlush(createMessage("agg-2", "topic.b", OutboxStatus.DEAD, 5, now.minusSeconds(1)));

        List<OutboxMessage> result = outboxRepository.findDead();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateId()).isEqualTo("agg-2");
        assertThat(result.get(0).getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    // ==================== Helper ====================

    private OutboxMessage createMessage(String aggregateId, String topic, OutboxStatus status,
                                        int retryCount, Instant nextRetryAt) {
        OutboxMessage msg = new OutboxMessage();
        msg.setAggregateId(aggregateId);
        msg.setTopic(topic);
        msg.setPayload("{\"test\":true}");
        msg.setStatus(status);
        msg.setRetryCount(retryCount);
        msg.setNextRetryAt(nextRetryAt);
        msg.setCreatedAt(Instant.now());
        return msg;
    }
}
