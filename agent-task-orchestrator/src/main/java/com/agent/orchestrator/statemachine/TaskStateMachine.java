package com.agent.orchestrator.statemachine;

import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

import java.util.Set;

/**
 * 任务状态机（10 状态），对齐 doc 03-task-engine §6.2 合法状态流转矩阵。
 *
 * <p>10 状态：PENDING / PLANNING / RUNNING / SUBTASK_RUNNING / WAITING_HUMAN /
 * REPLANNING / SUCCESS / FAILED / CANCELLED / TIMEOUT。
 * 终态：SUCCESS / FAILED / CANCELLED / TIMEOUT。</p>
 *
 * <p>合法流转矩阵由 {@link TaskStatus#getLegalNextStatuses()} 直接提供，
 * 状态机类无需重复维护映射表，仅负责校验与执行。</p>
 *
 * <p>禁止流转：所有未在 {@link TaskStatus#getLegalNextStatuses()} 中列出的转换均非法，
 * 特别是：</p>
 * <ul>
 *   <li>任何终态 → 非终态（除 FAILED/TIMEOUT → WAITING_HUMAN 的申诉路径）</li>
 *   <li>SUCCESS → 任何状态</li>
 *   <li>跳过 PLANNING 直接进入 SUBTASK_RUNNING（L1 除外，但 L1 经 PENDING→RUNNING→SUBTASK_RUNNING）</li>
 * </ul>
 */
public class TaskStateMachine {

    /**
     * 校验 from → to 是否合法（不实际执行状态变更）。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true 若转换合法；false 若非法
     */
    public boolean canTransitTo(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<TaskStatus> legalTargets = from.getLegalNextStatuses();
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
}
