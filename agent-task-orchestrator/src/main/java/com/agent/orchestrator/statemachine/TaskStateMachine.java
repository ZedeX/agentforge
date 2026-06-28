package com.agent.orchestrator.statemachine;

import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态机（10 状态），对齐 doc 03-task-engine §6.2 合法状态流转矩阵。
 *
 * <p>10 状态：PENDING / PLANNING / RUNNING / SUBTASK_RUNNING / WAITING_HUMAN /
 * REPLANNING / SUCCESS / FAILED / CANCELLED / TIMEOUT。
 * 终态：SUCCESS / FAILED / CANCELLED / TIMEOUT。</p>
 *
 * <p>合法流转矩阵（详见 doc §6.2 表格）：</p>
 * <ul>
 *   <li>PENDING → PLANNING, RUNNING(L1跳规划), FAILED, CANCELLED, TIMEOUT</li>
 *   <li>PLANNING → RUNNING, WAITING_HUMAN, FAILED, CANCELLED, TIMEOUT</li>
 *   <li>RUNNING → SUBTASK_RUNNING, WAITING_HUMAN, REPLANNING, FAILED, CANCELLED, TIMEOUT</li>
 *   <li>SUBTASK_RUNNING → WAITING_HUMAN, REPLANNING, SUCCESS, FAILED, CANCELLED, TIMEOUT</li>
 *   <li>WAITING_HUMAN → RUNNING(人工恢复), REPLANNING(人工触发), SUCCESS(人工确认), FAILED, CANCELLED</li>
 *   <li>REPLANNING → RUNNING(全量重规划完成), SUBTASK_RUNNING(增量完成继续跑), WAITING_HUMAN(熔断), FAILED, CANCELLED, TIMEOUT</li>
 *   <li>FAILED → WAITING_HUMAN(人工申诉)</li>
 *   <li>TIMEOUT → WAITING_HUMAN(人工申诉)</li>
 * </ul>
 *
 * <p>禁止流转：所有未列出的转换均非法，特别是：</p>
 * <ul>
 *   <li>任何终态 → 非终态（除 FAILED/TIMEOUT → WAITING_HUMAN 的申诉路径）</li>
 *   <li>SUCCESS → 任何状态</li>
 *   <li>跳过 PLANNING 直接进入 SUBTASK_RUNNING（L1 除外，但 L1 经 PENDING→RUNNING→SUBTASK_RUNNING）</li>
 * </ul>
 */
public class TaskStateMachine {

    /**
     * 状态流转矩阵：fromStatus -> Set&lt;legalToStatus&gt;。
     * 私有不可变，构造时一次性初始化。
     */
    private final Map<TaskStatus, Set<TaskStatus>> transitionMatrix;

    public TaskStateMachine() {
        this.transitionMatrix = buildTransitionMatrix();
    }

    /**
     * 校验 from → to 是否合法（不实际执行状态变更）。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true 若转换合法；false 若非法
     */
    public boolean canTransitTo(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> legalTargets = transitionMatrix.get(from);
        if (legalTargets == null) {
            return false;
        }
        return legalTargets.contains(to);
    }

    /**
     * 执行状态转换。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 转换后的目标状态（即 to）
     * @throws BusinessException 当 from → to 非法时，错误码为 {@link ErrorCode#PARAM_INVALID}
     */
    public TaskStatus transit(TaskStatus from, TaskStatus to) {
        if (!canTransitTo(from, to)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "非法状态转换: " + from + " → " + to);
        }
        return to;
    }

    /**
     * 构建状态流转矩阵，对齐 doc 03-task-engine §6.2 表格。
     */
    private static Map<TaskStatus, Set<TaskStatus>> buildTransitionMatrix() {
        Map<TaskStatus, Set<TaskStatus>> matrix = new EnumMap<>(TaskStatus.class);

        // PENDING → PLANNING, RUNNING(L1跳规划), FAILED, CANCELLED, TIMEOUT
        matrix.put(TaskStatus.PENDING, EnumSet.of(
                TaskStatus.PLANNING,
                TaskStatus.RUNNING,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.TIMEOUT));

        // PLANNING → RUNNING, WAITING_HUMAN, FAILED, CANCELLED, TIMEOUT
        matrix.put(TaskStatus.PLANNING, EnumSet.of(
                TaskStatus.RUNNING,
                TaskStatus.WAITING_HUMAN,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.TIMEOUT));

        // RUNNING → SUBTASK_RUNNING, WAITING_HUMAN, REPLANNING, FAILED, CANCELLED, TIMEOUT
        matrix.put(TaskStatus.RUNNING, EnumSet.of(
                TaskStatus.SUBTASK_RUNNING,
                TaskStatus.WAITING_HUMAN,
                TaskStatus.REPLANNING,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.TIMEOUT));

        // SUBTASK_RUNNING → WAITING_HUMAN, REPLANNING, SUCCESS, FAILED, CANCELLED, TIMEOUT
        matrix.put(TaskStatus.SUBTASK_RUNNING, EnumSet.of(
                TaskStatus.WAITING_HUMAN,
                TaskStatus.REPLANNING,
                TaskStatus.SUCCESS,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.TIMEOUT));

        // WAITING_HUMAN → RUNNING(人工恢复), REPLANNING(人工触发), SUCCESS(人工确认), FAILED, CANCELLED
        matrix.put(TaskStatus.WAITING_HUMAN, EnumSet.of(
                TaskStatus.RUNNING,
                TaskStatus.REPLANNING,
                TaskStatus.SUCCESS,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED));

        // REPLANNING → RUNNING(全量重规划完成), SUBTASK_RUNNING(增量完成继续跑), WAITING_HUMAN(熔断), FAILED, CANCELLED, TIMEOUT
        matrix.put(TaskStatus.REPLANNING, EnumSet.of(
                TaskStatus.RUNNING,
                TaskStatus.SUBTASK_RUNNING,
                TaskStatus.WAITING_HUMAN,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.TIMEOUT));

        // SUCCESS → (无，终态)
        matrix.put(TaskStatus.SUCCESS, EnumSet.noneOf(TaskStatus.class));

        // FAILED → WAITING_HUMAN(人工申诉)
        matrix.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.WAITING_HUMAN));

        // CANCELLED → (无，终态)
        matrix.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));

        // TIMEOUT → WAITING_HUMAN(人工申诉)
        matrix.put(TaskStatus.TIMEOUT, EnumSet.of(TaskStatus.WAITING_HUMAN));

        return matrix;
    }
}
