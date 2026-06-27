package com.agent.session.repository;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;
import com.agent.session.testinfra.fixture.SessionFixtures;
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

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
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
    void shouldSaveAndFindSessionById() {
        Session s = SessionFixtures.aSession("ss_save_001");
        sessionRepository.save(s);

        Optional<Session> found = sessionRepository.findBySessionId("ss_save_001");
        assertTrue(found.isPresent());
        assertEquals("u_001", found.get().getUserId());
        assertEquals(SessionStatus.ACTIVE.getCode(), found.get().getStatus());
    }

    @Test
    void shouldDeleteSessionById() {
        Session s = SessionFixtures.aSession("ss_del_001");
        sessionRepository.save(s);

        sessionRepository.deleteBySessionId("ss_del_001");

        assertTrue(sessionRepository.findBySessionId("ss_del_001").isEmpty());
    }

    @Test
    void shouldFindByTenantAndUserAndStatus() {
        sessionRepository.save(SessionFixtures.aSession("ss_q_001"));
        sessionRepository.save(SessionFixtures.aSession("ss_q_002"));

        List<Session> list = sessionRepository.findByTenantIdAndUserIdAndStatus(
                1001L, "u_001", SessionStatus.ACTIVE.getCode());

        assertEquals(2, list.size());
    }

    @Test
    void shouldSaveMessageAndQueryBySession() {
        sessionRepository.save(SessionFixtures.aSession("ss_msg_001"));

        Message m = SessionFixtures.aMessage("ss_msg_001", MessageRole.USER, "hello");
        m.setMsgId("msg_001");
        m.setTokenCount(10);
        messageRepository.save(m);

        List<Message> messages = messageRepository.findBySessionId("ss_msg_001");
        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).getContent());
    }

    @Test
    void shouldPageMessagesByCreatedAtAsc() {
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

        assertEquals(3, page.getContent().size());
        assertEquals(5, page.getTotalElements());
    }

    // FN-011 整改：本类原私有 newSession(String id) 方法已抽至公共 Fixture 工厂
    // com.agent.session.testinfra.fixture.SessionFixtures#aSession(String)，
    // 避免与 SessionControllerTest 各自维护一份造成散落。
}
