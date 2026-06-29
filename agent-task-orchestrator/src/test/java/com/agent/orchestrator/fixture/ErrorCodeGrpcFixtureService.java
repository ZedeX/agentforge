package com.agent.orchestrator.fixture;

import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.TaskOrchestratorGrpc;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.grpc.GrpcExceptionAdvice;
import io.grpc.stub.StreamObserver;

/**
 * P7-5 (COV-04) gRPC E2E 测试 fixture：
 *
 * <p>继承 proto 生成的 {@link TaskOrchestratorGrpc.TaskOrchestratorImplBase}，实现 {@code getTaskStatus}
 * 一个 RPC。根据 {@code request.getTaskId()} 字段解析为 {@link ErrorCode} 后抛出对应
 * {@link BusinessException}，再交由真实 {@link GrpcExceptionAdvice} 翻译为 gRPC Status 并通过
 * {@code observer.onError} 下发。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>路径设计：{@code taskId} 直接使用 ErrorCode 枚举名（如 {@code TASK_NOT_FOUND}），
 *       非法名字触发 {@link IllegalArgumentException}，覆盖「非 BusinessException → INTERNAL」分支。</li>
 *   <li>特殊指令 {@code _runtime}：直接抛 {@link RuntimeException}，不进入 BusinessException 路径，
 *       验证 {@link GrpcExceptionAdvice} 的兜底翻译行为。</li>
 *   <li>必须使用 try-catch + advice.translate 模式，与产品代码 {@code TaskOrchestratorGrpcService}
 *       保持一致；不能依赖 gRPC 框架自动异常翻译（避免丢上下文）。</li>
 * </ul>
 *
 * <p>该 fixture 仅在测试 classpath 中可见，不进入产品代码，不影响生产路由。</p>
 */
public class ErrorCodeGrpcFixtureService extends TaskOrchestratorGrpc.TaskOrchestratorImplBase {

    private final GrpcExceptionAdvice advice;

    public ErrorCodeGrpcFixtureService(GrpcExceptionAdvice advice) {
        this.advice = advice;
    }

    @Override
    public void getTaskStatus(GetTaskStatusRequest request,
                              StreamObserver<agentplatform.task.v1.TaskInstance> responseObserver) {
        try {
            String taskId = request.getTaskId();
            if ("_runtime".equals(taskId)) {
                throw new RuntimeException("runtime failure for e2e");
            }
            ErrorCode ec = ErrorCode.valueOf(taskId);
            throw new BusinessException(ec, "e2e:" + ec.getCode());
        } catch (Throwable t) {
            advice.translate(t, responseObserver);
        }
    }
}
