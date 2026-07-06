package com.agent.session.repository;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;
import com.agent.session.testinfra.fixture.SessionFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SessionRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("agent_session")
            .withUsername("root")
            .withPassword("root")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    @DisplayName("保存 Session 后应能按 sessionId 查询到对应记录")
    void should_SaveAndFindSessionById_When_SessionPersisted() {
        Session s = SessionFixtures.aSession("ss_save_001");
        sessionRepository.save(s);

        Optional<Session> found = sessionRepository.findBySessionId("ss_save_001");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("u_001");
        assertThat(found.get().getStatus()).isEqualTo(SessionStatus.ACTIVE.getCode());
    }

    @Test
    @DisplayName("按 sessionId 删除后查询应返回空")
    void should_DeleteSessionById_When_SessionExists() {
        Session s = SessionFixtures.aSession("ss_del_001");
        sessionRepository.save(s);

        sessionRepository.deleteBySessionId("ss_del_001");

        assertThat(sessionRepository.findBySessionId("ss_del_001")).isEmpty();
    }

    @Test
    @DisplayName("按租户/用户/状态组合查询应返回匹配的 Session 列表")
    void should_FindByTenantAndUserAndStatus_When_QueryCriteriaMatched() {
        sessionRepository.save(SessionFixtures.aSession("ss_q_001"));
        sessionRepository.save(SessionFixtures.aSession("ss_q_002"));

        List<Session> list = sessionRepository.findByTenantIdAndUserIdAndStatus(
                1001L, "u_001", SessionStatus.ACTIVE.getCode());

        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("保存 Message 后应能按 sessionId 查询到对应消息列表")
    void should_SaveMessageAndQueryBySession_When_MessagePersisted() {
        sessionRepository.save(SessionFixtures.aSession("ss_msg_001"));

        Message m = SessionFixtures.aMessage("ss_msg_001", MessageRole.USER, "hello");
        m.setMsgId("msg_001");
        m.setTokenCount(10);
        messageRepository.save(m);

        List<Message> messages = messageRepository.findBySessionId("ss_msg_001");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("分页查询 Message 应按 createdAt 升序返回指定页大小")
    void should_PageMessagesByCreatedAtAsc_When_PaginateQueryIssued() {
        sessionRepository.save(SessionFixtures.aSession("ss_page_001"));
        for (int i = 1; i <= 5; i++) {
            Message m = SessionFixtures.aMessage("ss_page_001", MessageRole.USER, "msg-" + i);
            m.setMsgId("msg_p_" + i);
            m.setTokenCount(i);
            messageRepository.save(m);
        }

        org.springframework.data.domain.Page<Message> page =
                messageRepository.findBySessionId("ss_page_001",
                        org.springframework.data.domain.PageRequest.of(0, 3,
                                org.springframework.data.domain.Sort.by("createdAt").ascending()));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5L);
    }

    // FN-011 整改：本类原私有 newSession(String id) 方法已抽至公共 Fixture 工厂
    // com.agent.session.testinfra.fixture.SessionFixtures#aSession(String)，
    // 避免与 SessionControllerTest 各自维护一份造成散落。
    // P6-3/4/5：方法名统一为 should_Xxx_When_Yyy；JUnit 断言替换为 AssertJ；补充中文 @DisplayName。
}
