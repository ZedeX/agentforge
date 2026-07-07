package com.agent.observability.grpc;

import agentplatform.observability.v1.GetHealthRequest;
import agentplatform.observability.v1.GetHealthResponse;
import agentplatform.observability.v1.GetMetricsRequest;
import agentplatform.observability.v1.GetMetricsResponse;
import agentplatform.observability.v1.GetTracesRequest;
import agentplatform.observability.v1.GetTracesResponse;
import com.agent.observability.api.HealthAggregator;
import com.agent.observability.api.MetricsCollector;
import com.agent.observability.api.TraceQueryService;
import com.agent.observability.model.ServiceHealth;
import com.agent.observability.model.TraceSummary;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link ObservabilityGrpcService}.
 *
 * <p>Verifies that the gRPC service correctly delegates to the business services
 * and translates results to proto responses.</p>
 */
@ExtendWith(MockitoExtension.class)
class ObservabilityGrpcServiceTest {

    @Mock
    private TraceQueryService traceQueryService;
    @Mock
    private MetricsCollector metricsCollector;
    @Mock
    private HealthAggregator healthAggregator;

    private ObservabilityGrpcService grpcService;

    @BeforeEach
    void setUp() {
        ObservabilityMapper mapper = new ObservabilityMapper();
        GrpcExceptionAdvice exceptionAdvice = new GrpcExceptionAdvice();
        grpcService = new ObservabilityGrpcService(
                traceQueryService, metricsCollector, healthAggregator,
                mapper, exceptionAdvice);
    }

    @Test
    void getTraces_shouldReturnTraces() {
        // Arrange
        GetTracesRequest request = GetTracesRequest.newBuilder()
                .setTraceId("trace-001")
                .build();
        com.agent.observability.model.GetTracesResponse pojoResponse =
                new com.agent.observability.model.GetTracesResponse(
                        List.of(new TraceSummary("trace-001", "agent-memory",
                                System.currentTimeMillis(), 150, 5, "ok"))
                );
        when(traceQueryService.query(any())).thenReturn(pojoResponse);

        // Act
        TestStreamObserver<GetTracesResponse> observer = new TestStreamObserver<>();
        grpcService.getTraces(request, observer);

        // Assert
        assertThat(observer.completed).isTrue();
        assertThat(observer.response).isNotNull();
        assertThat(observer.response.getTracesCount()).isEqualTo(1);
        assertThat(observer.response.getTraces(0).getTraceId()).isEqualTo("trace-001");
        verify(traceQueryService).query(any());
    }

    @Test
    void getMetrics_shouldReturnMetrics() {
        // Arrange
        GetMetricsRequest request = GetMetricsRequest.newBuilder()
                .setServiceName("agent-memory")
                .setMetricName("qps")
                .setStartTime(0)
                .setEndTime(System.currentTimeMillis())
                .build();
        com.agent.observability.model.GetMetricsResponse pojoResponse =
                new com.agent.observability.model.GetMetricsResponse(List.of());
        when(metricsCollector.collect(any())).thenReturn(pojoResponse);

        // Act
        TestStreamObserver<GetMetricsResponse> observer = new TestStreamObserver<>();
        grpcService.getMetrics(request, observer);

        // Assert
        assertThat(observer.completed).isTrue();
        assertThat(observer.response).isNotNull();
        verify(metricsCollector).collect(any());
    }

    @Test
    void getHealth_shouldReturnHealth() {
        // Arrange
        GetHealthRequest request = GetHealthRequest.newBuilder()
                .addServiceNames("agent-memory")
                .build();
        com.agent.observability.model.GetHealthResponse pojoResponse =
                new com.agent.observability.model.GetHealthResponse(
                        "healthy",
                        List.of(new ServiceHealth("agent-memory", "up", 3600, 0.0, 50.0, "ok"))
                );
        when(healthAggregator.aggregate(any())).thenReturn(pojoResponse);

        // Act
        TestStreamObserver<GetHealthResponse> observer = new TestStreamObserver<>();
        grpcService.getHealth(request, observer);

        // Assert
        assertThat(observer.completed).isTrue();
        assertThat(observer.response).isNotNull();
        assertThat(observer.response.getOverallStatus()).isEqualTo("healthy");
        assertThat(observer.response.getServicesCount()).isEqualTo(1);
        assertThat(observer.response.getServices(0).getServiceName()).isEqualTo("agent-memory");
        verify(healthAggregator).aggregate(any());
    }

    @Test
    void getTraces_shouldHandleException() {
        // Arrange
        GetTracesRequest request = GetTracesRequest.newBuilder().build();
        when(traceQueryService.query(any())).thenThrow(new RuntimeException("query failed"));

        // Act
        TestStreamObserver<GetTracesResponse> observer = new TestStreamObserver<>();
        grpcService.getTraces(request, observer);

        // Assert
        assertThat(observer.error).isNotNull();
        assertThat(observer.completed).isFalse();
    }

    /** Simple StreamObserver that captures response, completion, and error state. */
    private static class TestStreamObserver<T> implements StreamObserver<T> {
        T response;
        boolean completed;
        Throwable error;

        @Override
        public void onNext(T value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
