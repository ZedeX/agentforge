package com.agent.orchestrator.integration;

import agentplatform.common.v1.TraceContext;
import agentplatform.task.v1.CancelAck;
import agentplatform.task.v1.CancelTaskRequest;
import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.ReportAck;
import agentplatform.task.v1.SubtaskResult;
import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import agentplatform.task.v1.TaskOrchestratorGrpc;
import com.agent.common.constant.TaskStatus;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.grpc.GrpcExceptionAdvice;
import com.agent.orchestrator.grpc.TaskInstanceMapper;
import com.agent.orchestrator.grpc.TaskOrchestratorGrpcService;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationResult;
import com.github.fppt.jedismock.RedisServer;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 端到端集成测试（T13）：覆盖 6 个 E2E 场景。
 *
 * <p><b>基础设施（无 Docker）：</b></p>
 * <ul>
 *   <li>DB：H2 内存数据库（MODE=MySQL），Hibernate ddl-auto=create-drop 自动建表</li>
 *   <li>Redis：jedis-mock 嵌入式服务器（{@code RedisServer.newRedisServer().start()}），当前测试不依赖 Redis，仅验证基础设施可用</li>
 *   <li>gRPC：InProcess Server + Channel（grpc-testing），无需真实端口</li>
 *   <li>跨模块下游：Mockito stub（PlanValidator / TemplateMatcher / BatchPartitioner）</li>
 *   <li>本模块组件：真实 TaskInstanceRepository（H2）+ TaskStateMachine + TaskInstanceMapper + GrpcExceptionAdvice</li>
 * </ul>
 *
 * <p><b>事务处理设计：</b></p>
 * <p>{@code TaskOrchestratorGrpcService} 的 RPC 方法标注了 {@code @Transactional}，
 * 但本测试不启动 Spring 容器，直接 {@code new TaskOrchestratorGrpcService(...)} 不会触发事务代理。
 * 又因 gRPC 的 {@code bindService()} 生成的方法处理器引用 {@code this}（真实对象），CGLIB 代理的
 * 自调用问题（self-invocation）会导致方法处理器绕过代理。故采用子类覆盖每个 RPC 方法，
 * 在方法内用 {@link TransactionTemplate} 包裹 {@code super.xxx()} 调用，显式管理事务边界。</p>
 *
 * <p><b>覆盖场景：</b></p>
 * <ol>
 *   <li>E2E-1: L1 任务直接执行（跳规划）→ SUBTASK_RUNNING</li>
 *   <li>E2E-2: L2 任务走完整 PLANNING → VALIDATE → RUNNING 流程</li>
 *   <li>E2E-3: 子任务失败上报触发 REPLANNING</li>
 *   <li>E2E-4: 取消运行中任务 → CANCELLED</li>
 *   <li>E2E-5: GetTaskStatus 返回 DB 真实状态</li>
 *   <li>E2E-6: 查询不存在任务返回 gRPC NOT_FOUND</li>
 * </ol>
 */
class TaskOrchestratorIntegrationTest {

    private static HikariDataSource dataSource;
    private static EntityManagerFactory emf;
    private static JpaTransactionManager transactionManager;
    private static TransactionTemplate txTemplate;
    private static TaskInstanceRepository repository;

    private static RedisServer redisServer;
    private static Server grpcServer;
    private static ManagedChannel channel;
    private static TaskOrchestratorGrpc.TaskOrchestratorBlockingStub stub;

    // 真实本模块组件
    private static TaskStateMachine stateMachine;
    private static TaskInstanceMapper mapper;
    // Mockito stub 下游
    private static PlanValidator planValidator;
    private static TemplateMatcher templateMatcher;
    private static BatchPartitioner batchPartitioner;

    @BeforeAll
    static void setUp() throws Exception {
        // 1. H2 内存数据库（MySQL 兼容模式）
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:agent_task_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        // 2. Hibernate EMF（手动构造，不依赖 Spring Boot 上下文）+ ddl-auto=create-drop 自动建表
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan("com.agent.orchestrator.model");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties jpaProps = new Properties();
        jpaProps.put("hibernate.hbm2ddl.auto", "create-drop");
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        jpaProps.put("hibernate.show_sql", "false");
        jpaProps.put("hibernate.format_sql", "false");
        emfBean.setJpaProperties(jpaProps);
        emfBean.afterPropertiesSet();
        emf = emfBean.getNativeEntityManagerFactory();

        // 3. JpaTransactionManager + TransactionTemplate
        transactionManager = new JpaTransactionManager(emf);
        transactionManager.afterPropertiesSet();
        txTemplate = new TransactionTemplate(transactionManager);

        // 4. 真实 Repository（JpaRepositoryFactory + SharedEntityManager）
        //    SharedEntityManager 在事务内委托给线程绑定 EM，事务外新建 EM；
        //    配合 TransactionTemplate 可让 repository.save() 真正落库
        var sharedEm = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaRepositoryFactory repoFactory = new JpaRepositoryFactory(sharedEm);
        repository = repoFactory.getRepository(TaskInstanceRepository.class);

        // 5. 真实本模块组件 + Mockito stub 下游
        stateMachine = new TaskStateMachine();
        mapper = new TaskInstanceMapper();
        planValidator = mock(PlanValidator.class);
        templateMatcher = mock(TemplateMatcher.class);
        batchPartitioner = mock(BatchPartitioner.class);

        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();

        // 6. 构造 GrpcService 子类：用 TransactionTemplate 包裹每个 RPC 方法
        //    原因：直接 new 的实例不会触发 @Transactional 代理，且 gRPC bindService()
        //    生成的方法处理器引用 this（真实对象），CGLIB 代理自调用会绕过切面，
        //    故采用子类显式覆盖 + TransactionTemplate 管理事务边界
        TaskOrchestratorGrpcService service = new TaskOrchestratorGrpcService(
                repository, stateMachine, batchPartitioner, planValidator,
                templateMatcher, mapper, advice) {
            @Override
            public void submitTask(SubmitTaskRequest request,
                                   StreamObserver<SubmitTaskResponse> responseObserver) {
                txTemplate.executeWithoutResult(status -> super.submitTask(request, responseObserver));
            }

            @Override
            public void getTaskStatus(GetTaskStatusRequest request,
                                     StreamObserver<agentplatform.task.v1.TaskInstance> responseObserver) {
                txTemplate.executeWithoutResult(status -> super.getTaskStatus(request, responseObserver));
            }

            @Override
            public void cancelTask(CancelTaskRequest request,
                                   StreamObserver<CancelAck> responseObserver) {
                txTemplate.executeWithoutResult(status -> super.cancelTask(request, responseObserver));
            }

            @Override
            public void reportSubtaskResult(SubtaskResult request,
                                            StreamObserver<ReportAck> responseObserver) {
                txTemplate.executeWithoutResult(status -> super.reportSubtaskResult(request, responseObserver));
            }
        };

        // 7. InProcess gRPC Server（grpc-testing，无需真实端口）
        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = TaskOrchestratorGrpc.newBlockingStub(channel);

        // 8. jedis-mock 嵌入式 Redis（基础设施就绪，当前测试不依赖 Redis）
        redisServer = RedisServer.newRedisServer().start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (channel != null) {
            channel.shutdown();
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
        if (emf != null) {
            emf.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 在事务内保存任务实例（确保 persist 落库）。
     */
    private TaskInstance saveInTx(TaskInstance task) {
        return txTemplate.execute(status -> repository.save(task));
    }

    @Test
    @DisplayName("E2E-1: L1 任务提交应直接进入 SUBTASK_RUNNING（跳过 PLANNING）")
    void should_RunL1TaskDirectly_When_L1Submitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_e2e_l1")
                .setTenantId(1001L)
                .setUserId("u_1")
                .setGoal("查订单")
                .setCostLimitCent(10000L)
                .setTrace(TraceContext.newBuilder().setTaskId("tk_e2e_l1").build())
                .build();

        SubmitTaskResponse resp = stub.submitTask(req);

        assertThat(resp.getTaskId()).isEqualTo("tk_e2e_l1");
        assertThat(resp.getStatus()).isEqualTo(TaskStatus.SUBTASK_RUNNING.name());
        assertThat(resp.getComplexity()).isEqualTo(1);

        // 验证 DB 落库
        Optional<TaskInstance> saved = repository.findByTaskId("tk_e2e_l1");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(TaskStatus.SUBTASK_RUNNING.name());
    }

    @Test
    @DisplayName("E2E-2: L2 任务应走完整 PLANNING → VALIDATE → RUNNING 流程")
    void should_RunFullPlanningFlow_When_L2Submitted() {
        // assessComplexity 硬返回 1，故需预存 complexity=2 的任务让 GrpcService 走 L2 路径
        TaskInstance preTask = TaskInstance.builder()
                .taskId("tk_e2e_l2")
                .tenantId(1001L)
                .userId("u_1")
                .title("多步复杂任务")
                .goal("多步复杂任务")
                .complexity(2)
                .status(TaskStatus.PENDING.name())
                .taskSchema("{}")
                .priority(5)
                .costLimitCent(50000L)
                .costUsedCent(0L)
                .tokenUsed(0)
                .replanCount(0)
                .build();
        saveInTx(preTask);

        // stub PlanValidator.validate(ValidationContext) 返回通过
        when(planValidator.validate(any())).thenReturn(ValidationResult.pass());

        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_e2e_l2")
                .setGoal("多步复杂任务")
                .setCostLimitCent(50000L)
                .setTrace(TraceContext.newBuilder().setTaskId("tk_e2e_l2").build())
                .build();

        SubmitTaskResponse resp = stub.submitTask(req);

        assertThat(resp.getComplexity()).isEqualTo(2);
        assertThat(resp.getStatus())
                .isIn(TaskStatus.RUNNING.name(), TaskStatus.SUBTASK_RUNNING.name());
        verify(planValidator, atLeastOnce()).validate(any());
    }

    @Test
    @DisplayName("E2E-3: 子任务失败上报应触发 REPLANNING 状态")
    void should_TriggerReplanning_When_SubtaskFails() {
        // 预建 SUBTASK_RUNNING 任务
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_e2e_fail")
                .tenantId(1001L)
                .userId("u_1")
                .title("t")
                .goal("g")
                .status(TaskStatus.SUBTASK_RUNNING.name())
                .complexity(2)
                .costLimitCent(50000L)
                .costUsedCent(0L)
                .tokenUsed(0)
                .replanCount(0)
                .taskSchema("{}")
                .priority(5)
                .build();
        saveInTx(task);

        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_e2e_fail")
                .setSubtaskId("st_1")
                .setNodeId("n_1")
                .setStatus("failed")
                .setErrorCode("MAX_RETRY_EXCEEDED")
                .setCostCent(100L)
                .build();

        ReportAck ack = stub.reportSubtaskResult(result);

        assertThat(ack.getAccepted()).isTrue();
        Optional<TaskInstance> updated = repository.findByTaskId("tk_e2e_fail");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(TaskStatus.REPLANNING.name());
    }

    @Test
    @DisplayName("E2E-4: 取消运行中任务应转为 CANCELLED")
    void should_TransitToCancelled_When_CancelRunningTask() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_e2e_cancel")
                .tenantId(1001L)
                .userId("u_1")
                .title("t")
                .goal("g")
                .status(TaskStatus.RUNNING.name())
                .complexity(2)
                .costLimitCent(50000L)
                .costUsedCent(0L)
                .tokenUsed(0)
                .replanCount(0)
                .taskSchema("{}")
                .priority(5)
                .build();
        saveInTx(task);

        CancelTaskRequest req = CancelTaskRequest.newBuilder()
                .setTaskId("tk_e2e_cancel")
                .setReason("user cancel")
                .build();

        CancelAck ack = stub.cancelTask(req);

        assertThat(ack.getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
        Optional<TaskInstance> updated = repository.findByTaskId("tk_e2e_cancel");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
        assertThat(updated.get().getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("E2E-5: GetTaskStatus 应返回 DB 中真实任务状态")
    void should_ReturnRealStatus_When_GetTaskStatus() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_e2e_query")
                .tenantId(1001L)
                .userId("u_1")
                .title("t")
                .goal("g")
                .status(TaskStatus.PLANNING.name())
                .complexity(2)
                .costLimitCent(50000L)
                .costUsedCent(0L)
                .tokenUsed(0)
                .replanCount(0)
                .taskSchema("{}")
                .priority(5)
                .build();
        saveInTx(task);

        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_e2e_query")
                .setTenantId(1001L)
                .build();

        agentplatform.task.v1.TaskInstance proto = stub.getTaskStatus(req);

        assertThat(proto.getTaskId()).isEqualTo("tk_e2e_query");
        assertThat(proto.getStatus()).isEqualTo(TaskStatus.PLANNING.name());
    }

    @Test
    @DisplayName("E2E-6: 查询不存在的任务应返回 gRPC NOT_FOUND 错误")
    void should_ReturnNotFound_When_QueryNonExistentTask() {
        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_notexist")
                .setTenantId(1001L)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                io.grpc.StatusRuntimeException.class,
                () -> stub.getTaskStatus(req));
    }
}
