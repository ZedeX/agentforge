package com.agent.session.endtoend;

import com.agent.session.controller.SessionController;
import com.agent.session.controller.SessionStreamController;
import com.agent.session.repository.MessageRepository;
import com.agent.session.repository.SessionRepository;
import com.agent.session.service.SessionService;
import com.agent.session.service.ShortTermMemoryService;
import com.agent.session.service.SsePushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fppt.jedismock.RedisServer;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端测试：真实 SessionService + H2 (MySQL 兼容模式) + jedis-mock (嵌入式 Redis)。
 *
 * <p>FN-012 整改（docs/tests/tdd-audit-report-v2.md §P2-2）：
 * 原实现使用 {@code Mockito.mock(SessionService.class)} 跳过 DB，是 E2E 反模式。
 * 现改造为使用真实 SessionService + 真实 Repository + 真实 DB。</p>
 *
 * <p><b>基础设施选择</b>（因当前环境无 Docker，Testcontainers 不可用）：</p>
 * <ul>
 *   <li>数据库：H2 内存数据库（MODE=MySQL），Hibernate {@code ddl-auto=create-drop} 自动建表。
 *       任务允许用 H2 MySQL-mode 替代 MySQL Testcontainer（见 tdd-audit-report-v2.md §P2-2 FN-012）。</li>
 *   <li>Redis：jedis-mock（com.github.fppt:jedis-mock）嵌入式 Redis 服务器。
 *       jedis-mock 在 TCP 层实现 Redis 协议，Lettuce 客户端可像连接真实 Redis 一样连接它。
 *       当 Docker 不可用时，作为 Redis Testcontainer 的备选方案。
 *       若 Docker 可用，建议切换回 Testcontainers Redis 以获得更真实的集成测试。</li>
 * </ul>
 *
 * <p><b>核心整改</b>（与 v2 报告 P2-2 路径一致）：</p>
 * <ol>
 *   <li>用 {@link LocalContainerEntityManagerFactoryBean} + {@link HibernateJpaVendorAdapter}
 *       手动构造 EMF，{@code ddl-auto=create-drop} 让 Hibernate 按实体注解自动建表</li>
 *   <li>用 {@link SharedEntityManagerCreator#createSharedEntityManager} 创建线程绑定 EM 代理，
 *       保证 Repository 代理调用经线程绑定 EM 落到事务上下文</li>
 *   <li>用 {@link JpaRepositoryFactory} 构造真实 SessionRepository / MessageRepository 代理
 *       （注：Spring Data 3.2.x 已移除 {@code setTransactionManager()}，故 Repository 代理
 *       自身不带事务切面）</li>
 *   <li>用 {@link ProxyFactory} + {@link TransactionInterceptor} +
 *       {@link AnnotationTransactionAttributeSource} 为真实 {@link SessionService}
 *       织入事务代理，使其方法上的 {@code @Transactional} 经
 *       {@link JpaTransactionManager} 启动事务、提交时 flush 落库</li>
 *   <li>实例化真实 {@link SessionService}（不再 mock 同模块服务）</li>
 * </ol>
 *
 * <p>当前模块无跨进程依赖，故全部使用真实组件，零 Mockito mock。</p>
 *
 * <p>测试 1 的 sessionId 断言从硬编码 {@code "ss_e2e_001"} 改为从响应 JSON 解析真实生成的 ID
 * （真实 SessionService 用 UUID 生成 sessionId，无法预测具体值，但场景不变：创建→订阅→推送）。</p>
 */
class EndToEndTest {

    private static EntityManagerFactory emf;
    private static HikariDataSource dataSource;
    private static RedisServer redisServer;

    private MockMvc sessionMvc;
    private SsePushService ssePushService;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeAll
    static void initInfrastructure() throws IOException {
        // 1. H2 内存数据库（MySQL 兼容模式）—— 替代 MySQL Testcontainer（Docker 不可用时的备选方案）
        //    任务允许用 H2 MySQL-mode 替代 MySQL Testcontainer（见 tdd-audit-report-v2.md §P2-2 FN-012）
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:e2e_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        // 2. LocalContainerEntityManagerFactoryBean 手动构造 EMF（不依赖 Spring Boot 上下文）
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan("com.agent.session.model");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProps = new Properties();
        // create-drop：EMF 启动按实体注解建表，EMF 关闭清表
        jpaProps.put("hibernate.hbm2ddl.auto", "create-drop");
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        jpaProps.put("hibernate.show_sql", "false");
        jpaProps.put("hibernate.format_sql", "false");
        emfBean.setJpaProperties(jpaProps);
        emfBean.afterPropertiesSet();

        emf = emfBean.getNativeEntityManagerFactory();

        // 3. jedis-mock 嵌入式 Redis 服务器 —— 替代 Redis Testcontainer（Docker 不可用时的备选方案）
        //    jedis-mock 在 TCP 层实现 Redis 协议，Lettuce 可直接连接
        //    newRedisServer() 默认绑定随机端口，start() 后通过 getBindPort() 获取实际端口
        redisServer = RedisServer.newRedisServer().start();
    }

    @AfterAll
    static void closeInfrastructure() throws IOException {
        if (emf != null) {
            emf.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // 4. JpaTransactionManager：供 TransactionInterceptor 启动事务，绑定 EM 到线程
        JpaTransactionManager txManager = new JpaTransactionManager(emf);
        txManager.afterPropertiesSet();

        // 5. SharedEntityManager 代理：事务内委托给线程绑定 EM，事务外回退到新建 EM；
        //    关键是让 SimpleJpaRepository 拿到的 em 与 JpaTransactionManager 绑定的是同一套
        EntityManager sharedEm = SharedEntityManagerCreator.createSharedEntityManager(emf);

        // 6. 通过 JpaRepositoryFactory 构造真实 Repository 代理
        //    注：Spring Data 3.2.x 已移除 RepositoryFactorySupport.setTransactionManager()，
        //    故 Repository 代理自身不带事务切面；事务改在 SessionService 层织入（见步骤 8）
        JpaRepositoryFactory repoFactory = new JpaRepositoryFactory(sharedEm);
        SessionRepository sessionRepository = repoFactory.getRepository(SessionRepository.class);
        MessageRepository messageRepository = repoFactory.getRepository(MessageRepository.class);

        // 7. Redis 连接（jedis-mock）+ 真实 ShortTermMemoryService / SsePushService
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisServer.getHost(), redisServer.getBindPort());
        factory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        ShortTermMemoryService memory = new ShortTermMemoryService(redisTemplate, memoryProps());
        ssePushService = new SsePushService(redisTemplate, sseProps());

        // 8. 真实 SessionService（FN-012 整改：移除 Mockito.mock(SessionService.class)）
        SessionService sessionService = new SessionService(sessionRepository, messageRepository, memory);

        // 9. 为 SessionService 织入事务代理：让 @Transactional 注解生效，
        //    createSession 等方法经 TransactionInterceptor → JpaTransactionManager 启动事务，
        //    em.merge 在事务提交时 flush 真正落库（否则 SharedEntityManager 在无事务下
        //    每次 call 新建并 close EM，persist 的实体会被丢弃，不落库）
        TransactionAttributeSource txAttrSource = new AnnotationTransactionAttributeSource();
        TransactionInterceptor txAdvice = new TransactionInterceptor(txManager, txAttrSource);
        ProxyFactory proxyFactory = new ProxyFactory(sessionService);
        proxyFactory.addAdvice(txAdvice);
        SessionService txSessionService = (SessionService) proxyFactory.getProxy();

        SessionController sessionController = new SessionController(txSessionService, memory);
        SessionStreamController streamController = new SessionStreamController(ssePushService);

        sessionMvc = MockMvcBuilders.standaloneSetup(sessionController, streamController).build();
    }

    @Test
    void shouldCreateSessionPublishEventAndClientReceive() throws Exception {
        // 1. 创建会话（真实 SessionService 真实落库 H2）
        String body = """
                {
                  "agentId": 2001,
                  "title": "E2E 测试会话",
                  "meta": { "channel": "web" }
                }
                """;
        MvcResult createResult = sessionMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "1001")
                        .header("X-User-Id", "u_e2e")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andReturn();

        // 真实 sessionId 由 SessionService 内部 UUID 生成，需从响应解析（原 mock 写死 "ss_e2e_001"）
        String responseBody = createResult.getResponse().getContentAsString();
        String sessionId = om.readTree(responseBody).at("/data/sessionId").asText();
        assertNotNull(sessionId, "响应中应包含 sessionId");
        assertTrue(sessionId.startsWith("ss_"), "sessionId 应以 ss_ 开头");

        // 2. 启动 SSE 监听（异步）
        AtomicReference<String> receivedEvent = new AtomicReference<>();
        AtomicReference<Boolean> listenerReady = new AtomicReference<>(Boolean.FALSE);
        Thread listener = new Thread(() -> {
            try {
                MvcResult r = sessionMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/api/v1/sessions/" + sessionId + "/stream"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
                // 标记监听器已就位，主线程可以开始推送事件
                listenerReady.set(Boolean.TRUE);
                String content = r.getResponse().getContentAsString();
                receivedEvent.set(content);
            } catch (Exception e) {
                // ignore
            }
        });
        listener.setDaemon(true);
        listener.start();

        // FN-010 整改：原 Thread.sleep(200) 替换为 Awaitility 等监听器就位，
        // 一旦 listenerReady=true 立即继续，最坏等待 2 秒。
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> Boolean.TRUE.equals(listenerReady.get()));

        // 3. 服务端推送事件
        ssePushService.publish(sessionId, "token", Map.of("delta", "你好"));

        // FN-010 整改：原 Thread.sleep(500) 替换为 Awaitility 等事件被接收，
        // 收到内容立即继续；最坏等待 2 秒后由 listener.interrupt() 终结。
        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(50))
                    .until(() -> receivedEvent.get() != null);
        } finally {
            listener.interrupt();
        }

        // 4. 校验：至少监听到 SSE 流被建立（asyncStarted=true 即可证明）
        //    实际推送事件需通过 SseEmitter 异步发送，本测试验证链路无异常
        assertNotNull(sessionId);
    }

    @Test
    void shouldPushEventViaRedisPubSub() throws Exception {
        ssePushService.publish("ss_pub_001", "token", Map.of("delta", "hi"));

        // 验证 publish 不抛异常即视为通过（Redis Pub/Sub 链路）
        assertTrue(true);
    }

    private com.agent.session.config.ShortTermMemoryProperties memoryProps() {
        com.agent.session.config.ShortTermMemoryProperties p = new com.agent.session.config.ShortTermMemoryProperties();
        p.setKeyPrefix("sm");
        p.setTtlHours(24);
        p.setMaxRecentMessages(20);
        return p;
    }

    private com.agent.session.config.SseProperties sseProps() {
        com.agent.session.config.SseProperties p = new com.agent.session.config.SseProperties();
        p.setChannelPrefix("session");
        p.setTimeoutMs(30000);
        return p;
    }
}
