package com.agent.planning.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.planning.exception.PlanningErrorCode;
import com.agent.planning.exception.PlanningException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GrpcExceptionAdvice}.
 */
@DisplayName("GrpcExceptionAdvice")
class GrpcExceptionAdviceTest {

    private GrpcExceptionAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new GrpcExceptionAdvice();
    }

    @Test
    @DisplayName("Should_TranslateBusinessException_When_ParamInvalid -> INVALID_ARGUMENT")
    void should_TranslateBusinessException_When_ParamInvalid() {
        BusinessException ex = new BusinessException(ErrorCode.PARAM_INVALID, "bad param");
        CapturingObserver<Object> observer = new CapturingObserver<>();
        advice.translate(ex, observer);

        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Should_TranslateBusinessException_When_TaskNotFound -> NOT_FOUND")
    void should_TranslateBusinessException_When_TaskNotFound() {
        BusinessException ex = new BusinessException(ErrorCode.TASK_NOT_FOUND, "not found");
        CapturingObserver<Object> observer = new CapturingObserver<>();
        advice.translate(ex, observer);

        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @DisplayName("Should_TranslatePlanningException_When_InvalidDag -> INVALID_ARGUMENT")
    void should_TranslatePlanningException_When_InvalidDag() {
        PlanningException ex = new PlanningException(PlanningErrorCode.INVALID_DAG, "bad dag");
        CapturingObserver<Object> observer = new CapturingObserver<>();
        advice.translate(ex, observer);

        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Should_TranslatePlanningException_When_PlanNotFound -> NOT_FOUND")
    void should_TranslatePlanningException_When_PlanNotFound() {
        PlanningException ex = new PlanningException(PlanningErrorCode.PLAN_NOT_FOUND, "gone");
        CapturingObserver<Object> observer = new CapturingObserver<>();
        advice.translate(ex, observer);

        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @DisplayName("Should_TranslateIllegalArgumentException -> INVALID_ARGUMENT")
    void should_TranslateIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("bad arg");
        CapturingObserver<Object> observer = new CapturingObserver<>();
        advice.translate(ex, observer);

        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Should_TranslateRuntimeException -> INTERNAL")
    void should_TranslateRuntimeException() {
        RuntimeException ex = new RuntimeException("unexpected");
        CapturingObserver<Object> observer = new CapturingObserver<>();
        advice.translate(ex, observer);

        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    private static class CapturingObserver<T> implements StreamObserver<T> {
        final List<T> values = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) { values.add(value); }

        @Override
        public void onError(Throwable t) { error = t; }

        @Override
        public void onCompleted() { completed = true; }
    }
}
