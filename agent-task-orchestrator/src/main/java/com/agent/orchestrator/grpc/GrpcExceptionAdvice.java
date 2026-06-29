package com.agent.orchestrator.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * gRPC 异常翻译器：将 BusinessException 翻译为 gRPC Status，
 * 并通过 StreamObserver.onError 下发，避免服务方法抛未捕获异常导致 channel 异常断开。
 *
 * <p>错误码 → gRPC Status 映射（对齐 doc 03-task-engine §10.4）：</p>
 * <ul>
 *   <li>TASK_NOT_FOUND(404) / AGENT_NOT_FOUND(404) → NOT_FOUND</li>
 *   <li>TASK_STATUS_CONFLICT(409) / DAG_CYCLE_DETECTED(409) → FAILED_PRECONDITION</li>
 *   <li>COST_BUDGET_EXCEEDED(429) → RESOURCE_EXHAUSTED</li>
 *   <li>PARAM_INVALID(400) / VALIDATION_FAILED(400) → INVALID_ARGUMENT</li>
 *   <li>REPLAN_EXHAUSTED(500) / COMPLETENESS_FAIL(500) / INTERNAL(500) → INTERNAL</li>
 *   <li>非 BusinessException → INTERNAL with "internal error"</li>
 * </ul>
 *
 * <p>设计说明：采用 @Component + 手动调用 {@link #translate} 的方式，而非 net.devh 的
 * {@code @GrpcAdvice} + {@code @GrpcExceptionHandler} 注解模式。原因是服务层需要显式控制
 * onError 调用时机（在 try-catch 中），避免 gRPC 框架在异常传播时丢失上下文。</p>
 */
@Component
public class GrpcExceptionAdvice {

    /**
     * 将 Throwable 翻译为 gRPC Status 并通过 observer.onError 下发。
     *
     * @param t        业务异常或其他异常
     * @param observer gRPC 响应观察者
     * @param <T>      响应类型
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        observer.onError(status.asRuntimeException());
    }

    /**
     * 将 Throwable 翻译为 gRPC Status。
     *
     * @param t 异常
     * @return gRPC Status（含 Description）
     */
    private Status toStatus(Throwable t) {
        if (t instanceof BusinessException be) {
            ErrorCode ec = be.getErrorCode();
            return switch (ec.getHttpStatus()) {
                case 404 -> Status.NOT_FOUND.withDescription(ec.getCode() + ": " + be.getMessage());
                case 409 -> Status.FAILED_PRECONDITION.withDescription(ec.getCode() + ": " + be.getMessage());
                case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 400 -> Status.INVALID_ARGUMENT.withDescription(ec.getCode() + ": " + be.getMessage());
                default -> Status.INTERNAL.withDescription(ec.getCode() + ": " + be.getMessage());
            };
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }
}
