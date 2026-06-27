package com.agent.common.constant;

/**
 * 任务状态机 10 状态（doc 08-flow state-machines-and-sequences.md）
 *
 * 状态流转：PENDING → PLANNING → RUNNING ↔ SUBTASK_RUNNING → REPLANNING → RUNNING ...
 * 终态：SUCCESS / FAILED / CANCELLED / TIMEOUT
 */
public enum TaskStatus {

    PENDING(false),
    PLANNING(false),
    RUNNING(false),
    SUBTASK_RUNNING(false),
    WAITING_HUMAN(false),
    REPLANNING(false),
    SUCCESS(true),
    FAILED(true),
    CANCELLED(true),
    TIMEOUT(true);

    private final boolean terminal;

    TaskStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
