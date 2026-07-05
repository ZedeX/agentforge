package com.agent.runtime.grpc;

import agentplatform.agent_runtime.v1.AgentRuntimeGrpc;
import agentplatform.agent_runtime.v1.AgentState;
import agentplatform.agent_runtime.v1.GetStateRequest;
import agentplatform.agent_runtime.v1.PauseRequest;
import agentplatform.agent_runtime.v1.PauseResponse;
import agentplatform.agent_runtime.v1.ResumeRequest;
import agentplatform.agent_runtime.v1.ResumeResponse;
import agentplatform.agent_runtime.v1.StartAgentRequest;
import agentplatform.agent_runtime.v1.StartAgentResponse;
import agentplatform.agent_runtime.v1.StepRequest;
import agentplatform.agent_runtime.v1.StepResponse;
import com.agent.runtime.api.ReActLoop;
import com.agent.runtime.api.impl.SessionManager;
import com.agent.runtime.enums.SessionStatus;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReActResult;
import com.agent.runtime.repository.AgentSessionRepository;
import com.agent.runtime.repository.StepStateRepository;
import com.agent.runtime.repository.TokenUsageLogRepository;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T10 AgentRuntimeGrpcImpl unit tests (in-process gRPC server).
 *
 * <p>Tests the 5 RPCs through real gRPC wire, using @DataJpaTest for
 * SessionManager + JPA repositories, and a mock ReActLoop.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({SessionManager.class, GrpcExceptionAdvice.class})
class AgentRuntimeGrpcImplTest {

    @Autowired
    private AgentSessionRepository sessionRepository;

    @Autowired
    private StepStateRepository stepStateRepository;

    @Autowired
    private TokenUsageLogRepository tokenUsageLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private GrpcExceptionAdvice advice;

    private static Server grpcServer;
    private static ManagedChannel channel;
    private static AgentRuntimeGrpc.AgentRuntimeBlockingStub stub;
    private static ReActLoop mockReActLoop;
    private static TransactionTemplate txTemplate;

    @BeforeAll
    static void setUpClass() {
        mockReActLoop = mock(ReActLoop.class);
    }

    /**
     * Start in-process gRPC server before each test (needs Spring-managed beans).
     * Since @BeforeAll with static can't access instance fields, we use a lazy init pattern.
     */
    private AgentRuntimeGrpc.AgentRuntimeBlockingStub getStub() throws Exception {
        if (stub == null) {
            GrpcExceptionAdvice localAdvice = advice;
            SessionManager localSessionManager = sessionManager;

            AgentRuntimeGrpcImpl service = new AgentRuntimeGrpcImpl(
                    localSessionManager, mockReActLoop, localAdvice);

            String serverName = InProcessServerBuilder.generateName();
            grpcServer = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(service)
                    .build()
                    .start();
            channel = InProcessChannelBuilder.forName(serverName)
                    .directExecutor()
                    .build();
            stub = AgentRuntimeGrpc.newBlockingStub(channel);
        }
        return stub;
    }

    @AfterEach
    void cleanup() {
        // Clean up all data between tests
        tokenUsageLogRepository.deleteAll();
        stepStateRepository.deleteAll();
        sessionRepository.deleteAll();
        entityManager.flush();
    }

    @AfterAll
    static void tearDownClass() {
        if (channel != null) channel.shutdown();
        if (grpcServer != null) grpcServer.shutdown();
    }

    // ============ StartAgent ============

    @Test
    @DisplayName("startAgent: creates session and returns agentInstanceId")
    void startAgent_createsSession() throws Exception {
        StartAgentRequest request = StartAgentRequest.newBuilder()
                .setAgentId(1001L)
                .setAgentVersion(1)
                .setTaskId("task_001")
                .setSubtaskId("sub_001")
                .setNodeId("node_001")
                .setInputsJson("{\"input\":\"test\"}")
                .setMaxSteps(20)
                .setTokenBudget(32000)
                .setCostBudgetCent(10000L)
                .build();

        StartAgentResponse response = getStub().startAgent(request);

        assertThat(response.getAgentInstanceId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getStartedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("startAgent: rejects invalid agentId=0")
    void startAgent_rejectsInvalidAgentId() throws Exception {
        StartAgentRequest request = StartAgentRequest.newBuilder()
                .setAgentId(0L)
                .setTaskId("task_001")
                .setMaxSteps(20)
                .setTokenBudget(32000)
                .build();

        io.grpc.StatusRuntimeException ex = catchThrowableOfType(
                () -> getStub().startAgent(request),
                io.grpc.StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("startAgent: rejects empty taskId")
    void startAgent_rejectsEmptyTaskId() throws Exception {
        StartAgentRequest request = StartAgentRequest.newBuilder()
                .setAgentId(1001L)
                .setTaskId("")
                .setMaxSteps(20)
                .setTokenBudget(32000)
                .build();

        io.grpc.StatusRuntimeException ex = catchThrowableOfType(
                () -> getStub().startAgent(request),
                io.grpc.StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }

    // ============ GetState ============

    @Test
    @DisplayName("getState: returns session state")
    void getState_returnsSessionState() throws Exception {
        // First create a session
        StartAgentRequest startReq = StartAgentRequest.newBuilder()
                .setAgentId(2001L)
                .setAgentVersion(1)
                .setTaskId("task_002")
                .setMaxSteps(10)
                .setTokenBudget(16000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentResponse startResp = getStub().startAgent(startReq);

        GetStateRequest getStateReq = GetStateRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .build();

        AgentState state = getStub().getState(getStateReq);

        assertThat(state.getAgentInstanceId()).isEqualTo(startResp.getAgentInstanceId());
        assertThat(state.getTaskId()).isEqualTo("task_002");
        assertThat(state.getMaxSteps()).isEqualTo(10);
        assertThat(state.getTokenBudget()).isEqualTo(16000);
        assertThat(state.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("getState: NOT_FOUND for unknown agentInstanceId")
    void getState_notFoundForUnknown() throws Exception {
        GetStateRequest req = GetStateRequest.newBuilder()
                .setAgentInstanceId("nonexistent_id")
                .build();

        io.grpc.StatusRuntimeException ex = catchThrowableOfType(
                () -> getStub().getState(req),
                io.grpc.StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
    }

    // ============ Pause / Resume ============

    @Test
    @DisplayName("pause: pauses a running session")
    void pause_pausesRunningSession() throws Exception {
        StartAgentRequest startReq = StartAgentRequest.newBuilder()
                .setAgentId(3001L)
                .setAgentVersion(1)
                .setTaskId("task_003")
                .setMaxSteps(10)
                .setTokenBudget(16000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentResponse startResp = getStub().startAgent(startReq);

        PauseRequest pauseReq = PauseRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .setReason("manual pause")
                .build();

        PauseResponse pauseResp = getStub().pause(pauseReq);

        assertThat(pauseResp.getAgentInstanceId()).isEqualTo(startResp.getAgentInstanceId());
        assertThat(pauseResp.getStatus()).isEqualTo("PAUSED");
        assertThat(pauseResp.getPausedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("resume: resumes a paused session")
    void resume_resumesPausedSession() throws Exception {
        StartAgentRequest startReq = StartAgentRequest.newBuilder()
                .setAgentId(4001L)
                .setAgentVersion(1)
                .setTaskId("task_004")
                .setMaxSteps(10)
                .setTokenBudget(16000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentResponse startResp = getStub().startAgent(startReq);

        // Pause first
        PauseRequest pauseReq = PauseRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .setReason("pause before resume")
                .build();
        getStub().pause(pauseReq);

        // Then resume
        ResumeRequest resumeReq = ResumeRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .build();

        ResumeResponse resumeResp = getStub().resume(resumeReq);

        assertThat(resumeResp.getAgentInstanceId()).isEqualTo(startResp.getAgentInstanceId());
        assertThat(resumeResp.getStatus()).isEqualTo("RUNNING");
        assertThat(resumeResp.getResumedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("pause: FAILED_PRECONDITION when session not RUNNING")
    void pause_failsWhenNotRunning() throws Exception {
        StartAgentRequest startReq = StartAgentRequest.newBuilder()
                .setAgentId(5001L)
                .setAgentVersion(1)
                .setTaskId("task_005")
                .setMaxSteps(10)
                .setTokenBudget(16000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentResponse startResp = getStub().startAgent(startReq);

        // Pause once
        PauseRequest pauseReq = PauseRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .build();
        getStub().pause(pauseReq);

        // Try to pause again — should fail (already PAUSED)
        io.grpc.StatusRuntimeException ex = catchThrowableOfType(
                () -> getStub().pause(pauseReq),
                io.grpc.StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("resume: FAILED_PRECONDITION when session not PAUSED")
    void resume_failsWhenNotPaused() throws Exception {
        StartAgentRequest startReq = StartAgentRequest.newBuilder()
                .setAgentId(6001L)
                .setAgentVersion(1)
                .setTaskId("task_006")
                .setMaxSteps(10)
                .setTokenBudget(16000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentResponse startResp = getStub().startAgent(startReq);

        // Session is RUNNING; try to resume — should fail
        ResumeRequest resumeReq = ResumeRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .build();

        io.grpc.StatusRuntimeException ex = catchThrowableOfType(
                () -> getStub().resume(resumeReq),
                io.grpc.StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.FAILED_PRECONDITION);
    }

    // ============ Step ============

    @Test
    @DisplayName("step: executes with mock ReActLoop")
    void step_executesWithMockLoop() throws Exception {
        // Mock ReActLoop to return a success result
        ReActResult mockResult = ReActResult.success("ai_test", "sess_test",
                "final answer", 1, 100, 50L);
        when(mockReActLoop.run(any(ReActContext.class))).thenReturn(mockResult);

        StartAgentRequest startReq = StartAgentRequest.newBuilder()
                .setAgentId(7001L)
                .setAgentVersion(1)
                .setTaskId("task_007")
                .setMaxSteps(10)
                .setTokenBudget(16000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentResponse startResp = getStub().startAgent(startReq);

        StepRequest stepReq = StepRequest.newBuilder()
                .setAgentInstanceId(startResp.getAgentInstanceId())
                .build();

        StepResponse stepResp = getStub().step(stepReq);

        assertThat(stepResp.getAgentInstanceId()).isEqualTo(startResp.getAgentInstanceId());
        assertThat(stepResp.getFinished()).isTrue();
    }

    @Test
    @DisplayName("step: NOT_FOUND for unknown agentInstanceId")
    void step_notFoundForUnknown() throws Exception {
        StepRequest req = StepRequest.newBuilder()
                .setAgentInstanceId("nonexistent_step_id")
                .build();

        io.grpc.StatusRuntimeException ex = catchThrowableOfType(
                () -> getStub().step(req),
                io.grpc.StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
    }
}
