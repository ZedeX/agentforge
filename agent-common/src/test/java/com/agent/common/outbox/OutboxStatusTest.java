package com.agent.common.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Red: Failing tests for {@link OutboxStatus} enum.
 *
 * <p>Validates the state machine: PENDING → SENT / FAILED → DEAD.</p>
 */
class OutboxStatusTest {

    @Test
    @DisplayName("OutboxStatus should define exactly 4 states: PENDING, SENT, FAILED, DEAD")
    void should_HaveFourStates_When_OutboxStatusEnumLoaded() {
        OutboxStatus[] values = OutboxStatus.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactlyInAnyOrder(
                OutboxStatus.PENDING, OutboxStatus.SENT, OutboxStatus.FAILED, OutboxStatus.DEAD);
    }

    @Test
    @DisplayName("PENDING.canTransitionTo(SENT) should return true")
    void should_AllowPendingToSent_When_CanTransitionToCalled() {
        assertThat(OutboxStatus.PENDING.canTransitionTo(OutboxStatus.SENT)).isTrue();
    }

    @Test
    @DisplayName("PENDING.canTransitionTo(FAILED) should return true")
    void should_AllowPendingToFailed_When_CanTransitionToCalled() {
        assertThat(OutboxStatus.PENDING.canTransitionTo(OutboxStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("FAILED.canTransitionTo(PENDING) should return true (retry)")
    void should_AllowFailedToPending_When_CanTransitionToCalled() {
        assertThat(OutboxStatus.FAILED.canTransitionTo(OutboxStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("FAILED.canTransitionTo(DEAD) should return true (exceeded max retries)")
    void should_AllowFailedToDead_When_CanTransitionToCalled() {
        assertThat(OutboxStatus.FAILED.canTransitionTo(OutboxStatus.DEAD)).isTrue();
    }

    @Test
    @DisplayName("SENT is terminal: canTransitionTo(any) should return false")
    void should_NotAllowSentToAnything_When_CanTransitionToCalled() {
        for (OutboxStatus target : OutboxStatus.values()) {
            assertThat(OutboxStatus.SENT.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("DEAD is terminal: canTransitionTo(any) should return false")
    void should_NotAllowDeadToAnything_When_CanTransitionToCalled() {
        for (OutboxStatus target : OutboxStatus.values()) {
            assertThat(OutboxStatus.DEAD.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("PENDING cannot transition to itself or DEAD directly")
    void should_NotAllowPendingToSelfOrDead_When_CanTransitionToCalled() {
        assertThat(OutboxStatus.PENDING.canTransitionTo(OutboxStatus.PENDING)).isFalse();
        assertThat(OutboxStatus.PENDING.canTransitionTo(OutboxStatus.DEAD)).isFalse();
    }

    @Test
    @DisplayName("FAILED cannot transition to SENT (must go through PENDING retry first)")
    void should_NotAllowFailedToSent_When_CanTransitionToCalled() {
        assertThat(OutboxStatus.FAILED.canTransitionTo(OutboxStatus.SENT)).isFalse();
    }
}
