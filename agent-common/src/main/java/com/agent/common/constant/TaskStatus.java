package com.agent.common.constant;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 * 任务状态机 10 状态（doc 03-task-engine §6.1-6.2）。
 *
 * <p>状态流转：PENDING → PLANNING → RUNNING ↔ SUBTASK_RUNNING → REPLANNING → RUNNING ...
 * 终态：SUCCESS / FAILED / CANCELLED / TIMEOUT。</p>
 *
 * <p>每个状态携带以下元信息：</p>
 * <ul>
 *   <li>{@code terminal}：是否终态</li>
 *   <li>合法下一状态集合：通过 {@link #getLegalNextStatuses()} 查询内部静态流转矩阵</li>
 * </ul>
 *
 * <p>流转矩阵由 static block 在所有枚举常量初始化后一次性填充，
 * 避免在构造器参数中前向引用尚未创建的枚举实例（Java 编译器禁止此类前向引用），
 * 同时让 {@code TaskStateMachine} 无需重复维护映射表。</p>
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

    /**
     * 合法状态流转矩阵（doc §6.2）。
     *
     * <p>使用 static block 在所有枚举实例创建后一次性填充，
     * 避免在构造器中前向引用未初始化的枚举常量。
     * 矩阵的 Set 与 Map 均为不可变视图，防止外部修改。</p>
     */
    private static final Map<TaskStatus, Set<TaskStatus>> LEGAL_NEXT_STATUSES;

    static {
        Map<TaskStatus, Set<TaskStatus>> matrix = new EnumMap<>(TaskStatus.class);
        matrix.put(PENDING, unmodifiableSet(EnumSet.of(PLANNING, RUNNING, FAILED, CANCELLED, TIMEOUT)));
        matrix.put(PLANNING, unmodifiableSet(EnumSet.of(RUNNING, WAITING_HUMAN, FAILED, CANCELLED, TIMEOUT)));
        matrix.put(RUNNING, unmodifiableSet(EnumSet.of(SUBTASK_RUNNING, WAITING_HUMAN, REPLANNING, FAILED, CANCELLED, TIMEOUT)));
        matrix.put(SUBTASK_RUNNING, unmodifiableSet(EnumSet.of(WAITING_HUMAN, REPLANNING, SUCCESS, FAILED, CANCELLED, TIMEOUT)));
        matrix.put(WAITING_HUMAN, unmodifiableSet(EnumSet.of(RUNNING, REPLANNING, SUCCESS, FAILED, CANCELLED)));
        matrix.put(REPLANNING, unmodifiableSet(EnumSet.of(RUNNING, SUBTASK_RUNNING, WAITING_HUMAN, FAILED, CANCELLED, TIMEOUT)));
        matrix.put(SUCCESS, unmodifiableSet(EnumSet.noneOf(TaskStatus.class)));
        matrix.put(FAILED, unmodifiableSet(EnumSet.of(WAITING_HUMAN)));
        matrix.put(CANCELLED, unmodifiableSet(EnumSet.noneOf(TaskStatus.class)));
        matrix.put(TIMEOUT, unmodifiableSet(EnumSet.of(WAITING_HUMAN)));
        LEGAL_NEXT_STATUSES = unmodifiableMap(matrix);
    }

    TaskStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 返回此状态合法可流转到的下一状态集合（不可变）。
     *
     * <p>对应 doc 03-task-engine §6.2 流转矩阵中该状态所在行的合法列。
     * 终态返回空集合。</p>
     */
    public Set<TaskStatus> getLegalNextStatuses() {
        return LEGAL_NEXT_STATUSES.get(this);
    }
}
