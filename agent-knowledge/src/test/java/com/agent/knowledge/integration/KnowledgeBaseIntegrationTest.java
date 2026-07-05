package com.agent.knowledge.integration;

import agentplatform.common.v1.TraceContext;
import agentplatform.knowledge.v1.DeleteBaseRequest;
import agentplatform.knowledge.v1.DeleteBaseResponse;
import agentplatform.knowledge.v1.IngestDocumentRequest;
import agentplatform.knowledge.v1.IngestDocumentResponse;
import agentplatform.knowledge.v1.KnowledgeServiceGrpc;
import agentplatform.knowledge.v1.ListBasesRequest;
import agentplatform.knowledge.v1.ListBasesResponse;
import agentplatform.knowledge.v1.SearchChunksRequest;
import agentplatform.knowledge.v1.SearchChunksResponse;
import com.agent.knowledge.api.impl.ChunkSplitterImpl;
import com.agent.knowledge.api.impl.DocumentIngestorImpl;
import com.agent.knowledge.api.impl.DocumentParserImpl;
import com.agent.knowledge.api.impl.KnowledgeBaseServiceImpl;
import com.agent.knowledge.api.impl.KnowledgeRetrieverImpl;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.grpc.GrpcExceptionAdvice;
import com.agent.knowledge.grpc.KnowledgeBaseGrpcService;
import com.agent.knowledge.grpc.KnowledgeMapper;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.repository.DocumentChunkRepository;
import com.agent.knowledge.repository.KnowledgeBaseRepository;
import com.agent.knowledge.repository.KnowledgeDocumentRepository;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 端到端集成测试（Plan 08 T12）：覆盖 6 个 E2E 场景。
 *
 * <p><b>基础设施（无 Docker）：</b></p>
 * <ul>
 *   <li>DB：H2 内存数据库（MODE=MySQL），Hibernate ddl-auto=create-drop 自动建表</li>
 *   <li>gRPC：InProcess Server + Channel（grpc-testing），无需真实端口</li>
 *   <li>本模块组件：真实 JPA repositories（H2）+ 真实 DocumentParserImpl / ChunkSplitterImpl /
 *       DocumentIngestorImpl / KnowledgeBaseServiceImpl / KnowledgeRetrieverImpl +
 *       真实 KnowledgeMapper + GrpcExceptionAdvice</li>
 *   <li>无 Spring 容器：手动 new 各组件，无 @SpringBootTest</li>
 * </ul>
 *
 * <p><b>事务处理设计：</b></p>
 * <p>{@link KnowledgeBaseServiceImpl} 和 {@link DocumentIngestorImpl} 的写方法标注了
 * {@code @Transactional}，但本测试不启动 Spring 容器，直接 {@code new} 不会触发事务代理。
 * 故采用子类覆盖每个写 RPC 方法，在方法内用 {@link TransactionTemplate} 包裹
 * {@code super.xxx()} 调用，显式管理事务边界。</p>
 *
 * <p><b>覆盖场景：</b></p>
 * <ol>
 *   <li>E2E-1: 创建 KB → IngestDocument → 验证 chunk_count + doc_count</li>
 *   <li>E2E-2: ListBases → 验证列表 + status 过滤</li>
 *   <li>E2E-3: DeleteBase 含文档且 force=false → KB_IN_USE FAILED_PRECONDITION</li>
 *   <li>E2E-4: DeleteBase force=true → KB 标记 DELETED</li>
 *   <li>E2E-5: DeleteBase 不存在 → NOT_FOUND</li>
 *   <li>E2E-6: SearchChunks（空索引 → 返回空列表，非错误）</li>
 * </ol>
 */
@DisplayName("KnowledgeBase 端到端集成测试（Plan 08 T12）")
class KnowledgeBaseIntegrationTest {

    private static HikariDataSource dataSource;
    private static EntityManagerFactory emf;
    private static JpaTransactionManager transactionManager;
    private static TransactionTemplate txTemplate;

    private static KnowledgeBaseRepository kbRepository;
    private static DocumentChunkRepository chunkRepository;
    private static KnowledgeDocumentRepository documentRepository;

    private static Server grpcServer;
    private static ManagedChannel channel;
    private static KnowledgeServiceGrpc.KnowledgeServiceBlockingStub stub;

    @BeforeAll
    static void setUp() throws Exception {
        // 1. H2 内存数据库（MySQL 兼容模式）
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:agent_knowledge_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        // 2. Hibernate EMF（手动构造，不依赖 Spring Boot 上下文）
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan("com.agent.knowledge.model");
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
        var sharedEm = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaRepositoryFactory repoFactory = new JpaRepositoryFactory(sharedEm);
        kbRepository = repoFactory.getRepository(KnowledgeBaseRepository.class);
        chunkRepository = repoFactory.getRepository(DocumentChunkRepository.class);
        documentRepository = repoFactory.getRepository(KnowledgeDocumentRepository.class);

        // 5. 真实本模块组件
        DocumentParserImpl documentParser = new DocumentParserImpl();
        ChunkSplitterImpl chunkSplitter = new ChunkSplitterImpl();
        DocumentIngestorImpl documentIngestor = new DocumentIngestorImpl(
                documentParser, chunkSplitter, chunkRepository, kbRepository, documentRepository);
        KnowledgeRetrieverImpl knowledgeRetriever = new KnowledgeRetrieverImpl();
        KnowledgeBaseServiceImpl kbService = new KnowledgeBaseServiceImpl(
                kbRepository, documentIngestor, knowledgeRetriever);

        KnowledgeMapper mapper = new KnowledgeMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();

        // 6. 构造 GrpcService 子类：写 RPC 用 TransactionTemplate 包裹
        //    原因：直接 new 的实例不会触发 @Transactional 代理
        KnowledgeBaseGrpcService service = new KnowledgeBaseGrpcService(
                kbService, mapper, advice) {
            @Override
            public void ingestDocument(IngestDocumentRequest request,
                                       StreamObserver<IngestDocumentResponse> responseObserver) {
                txTemplate.executeWithoutResult(status -> super.ingestDocument(request, responseObserver));
            }

            @Override
            public void deleteBase(DeleteBaseRequest request,
                                   StreamObserver<DeleteBaseResponse> responseObserver) {
                txTemplate.executeWithoutResult(status -> super.deleteBase(request, responseObserver));
            }
        };

        // 7. InProcess gRPC Server
        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = KnowledgeServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        if (emf != null) {
            emf.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 在事务内创建 KB（确保 persist 落库）。
     */
    private KnowledgeBase createKbInTx(String kbId, String name, String description) {
        return txTemplate.execute(status -> {
            KnowledgeBase kb = new KnowledgeBase(kbId, name);
            kb.setDescription(description);
            kb.setStatus(KnowledgeStatus.READY);
            kb.setDocCount(0);
            kb.setChunkCount(0);
            return kbRepository.save(kb);
        });
    }

    // ===== E2E-1: Create KB → IngestDocument =====

    @Test
    @DisplayName("E2E-1: 创建 KB → IngestDocument 应返回 chunk_ids + 更新 KB 统计")
    void should_IngestDocument_When_KbExists() {
        createKbInTx("kb-e2e-001", "测试知识库", "E2E 测试");

        IngestDocumentRequest req = IngestDocumentRequest.newBuilder()
                .setKbId("kb-e2e-001")
                .setDocId("doc-e2e-001")
                .setName("测试文档")
                .setContent("This is a test document for knowledge base integration test. "
                        + "It contains multiple sentences to ensure proper chunking. "
                        + "The chunk splitter should split this content into manageable pieces.")
                .setType("TEXT")
                .setChunkStrategy("TOKEN")
                .setMaxTokens(50)
                .setOverlap(10)
                .setTrace(TraceContext.newBuilder().setTaskId("trace-1").build())
                .build();

        IngestDocumentResponse resp = stub.ingestDocument(req);

        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getDocId()).isEqualTo("doc-e2e-001");
        assertThat(resp.getKbId()).isEqualTo("kb-e2e-001");
        assertThat(resp.getChunkIdsCount()).isGreaterThan(0);
        assertThat(resp.getChunkCount()).isEqualTo(resp.getChunkIdsCount());
        assertThat(resp.getTokenCount()).isGreaterThan(0);

        // Verify KB stats updated
        KnowledgeBase kb = kbRepository.findByKbId("kb-e2e-001").orElseThrow();
        assertThat(kb.getDocCount()).isEqualTo(1);
        assertThat(kb.getChunkCount()).isEqualTo(resp.getChunkIdsCount());
    }

    // ===== E2E-2: ListBases =====

    @Test
    @DisplayName("E2E-2: ListBases 应返回所有非 DELETED 知识库 + 支持 status 过滤")
    void should_ListBases_When_MultipleKbsExist() {
        createKbInTx("kb-list-001", "列表知识库 A", "first");
        createKbInTx("kb-list-002", "列表知识库 B", "second");
        createKbInTx("kb-list-003", "列表知识库 C", "third");

        // List all (no status filter)
        ListBasesRequest listReq = ListBasesRequest.newBuilder()
                .setPageSize(10)
                .setTrace(TraceContext.newBuilder().setTaskId("trace-list").build())
                .build();
        ListBasesResponse listResp = stub.listBases(listReq);

        assertThat(listResp.getTotal()).isGreaterThanOrEqualTo(3);
        assertThat(listResp.getBasesCount()).isGreaterThanOrEqualTo(3);

        // Filter by status = READY
        ListBasesRequest filterReq = ListBasesRequest.newBuilder()
                .setStatus("READY")
                .setPageSize(10)
                .build();
        ListBasesResponse filterResp = stub.listBases(filterReq);
        assertThat(filterResp.getTotal()).isGreaterThanOrEqualTo(3);
        for (var base : filterResp.getBasesList()) {
            assertThat(base.getStatus()).isEqualTo("READY");
        }
    }

    // ===== E2E-3: DeleteBase with docs (no force) → KB_IN_USE =====

    @Test
    @DisplayName("E2E-3: DeleteBase 含文档且 force=false 应返回 FAILED_PRECONDITION (KB_IN_USE)")
    void should_ReturnFailedPrecondition_When_DeleteKbWithDocsWithoutForce() {
        createKbInTx("kb-delete-001", "待删除知识库", "has docs");

        // Ingest a document first
        IngestDocumentRequest ingestReq = IngestDocumentRequest.newBuilder()
                .setKbId("kb-delete-001")
                .setDocId("doc-delete-001")
                .setName("文档")
                .setContent("Some content to ingest for delete test scenario.")
                .setType("TEXT")
                .setChunkStrategy("TOKEN")
                .setMaxTokens(50)
                .setOverlap(10)
                .build();
        stub.ingestDocument(ingestReq);

        // Attempt delete without force
        DeleteBaseRequest deleteReq = DeleteBaseRequest.newBuilder()
                .setKbId("kb-delete-001")
                .setForce(false)
                .build();

        assertThatThrownBy(() -> stub.deleteBase(deleteReq))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                });

        // Verify KB still exists (not deleted)
        KnowledgeBase kb = kbRepository.findByKbId("kb-delete-001").orElseThrow();
        assertThat(kb.getStatus()).isNotEqualTo(KnowledgeStatus.DELETED);
    }

    // ===== E2E-4: DeleteBase force=true → DELETED =====

    @Test
    @DisplayName("E2E-4: DeleteBase force=true 应标记 KB 为 DELETED")
    void should_MarkAsDeleted_When_DeleteWithForce() {
        createKbInTx("kb-force-delete-001", "强制删除知识库", "will be force deleted");

        // Ingest a document
        IngestDocumentRequest ingestReq = IngestDocumentRequest.newBuilder()
                .setKbId("kb-force-delete-001")
                .setDocId("doc-force-001")
                .setName("文档")
                .setContent("Content for force delete test scenario.")
                .setType("TEXT")
                .setChunkStrategy("TOKEN")
                .setMaxTokens(50)
                .setOverlap(10)
                .build();
        stub.ingestDocument(ingestReq);

        // Delete with force
        DeleteBaseRequest deleteReq = DeleteBaseRequest.newBuilder()
                .setKbId("kb-force-delete-001")
                .setForce(true)
                .build();
        DeleteBaseResponse deleteResp = stub.deleteBase(deleteReq);

        assertThat(deleteResp.getSuccess()).isTrue();
        assertThat(deleteResp.getKbId()).isEqualTo("kb-force-delete-001");

        // Verify KB is marked DELETED
        KnowledgeBase kb = kbRepository.findByKbId("kb-force-delete-001").orElseThrow();
        assertThat(kb.getStatus()).isEqualTo(KnowledgeStatus.DELETED);

        // Subsequent delete/ingest should fail with NOT_FOUND (deleted KB treated as not found)
        DeleteBaseRequest secondDelete = DeleteBaseRequest.newBuilder()
                .setKbId("kb-force-delete-001")
                .setForce(true)
                .build();
        assertThatThrownBy(() -> stub.deleteBase(secondDelete))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                });
    }

    // ===== E2E-5: DeleteBase not found =====

    @Test
    @DisplayName("E2E-5: DeleteBase 不存在应返回 NOT_FOUND")
    void should_ReturnNotFound_When_DeleteNonExistentKb() {
        DeleteBaseRequest req = DeleteBaseRequest.newBuilder()
                .setKbId("non-existent-kb")
                .setForce(true)
                .build();

        assertThatThrownBy(() -> stub.deleteBase(req))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                });
    }

    // ===== E2E-6: SearchChunks (empty index → empty results) =====

    @Test
    @DisplayName("E2E-6: SearchChunks 未索引应返回空列表（非错误）")
    void should_ReturnEmpty_When_NoChunksIndexed() {
        createKbInTx("kb-search-001", "搜索测试知识库", "search test");

        SearchChunksRequest req = SearchChunksRequest.newBuilder()
                .setKbId("kb-search-001")
                .setQuery("测试查询")
                .setTopK(5)
                .setEnableMmr(false)
                .setTrace(TraceContext.newBuilder().setTaskId("trace-search").build())
                .build();

        SearchChunksResponse resp = stub.searchChunks(req);

        // KnowledgeRetrieverImpl is in-memory and not auto-indexed by DocumentIngestorImpl,
        // so search returns empty list (not an error)
        assertThat(resp.getTotalHits()).isEqualTo(0);
        assertThat(resp.getChunksCount()).isEqualTo(0);
    }
}
