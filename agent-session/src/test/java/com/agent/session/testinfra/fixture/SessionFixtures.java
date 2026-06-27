package com.agent.session.testinfra.fixture;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;

/**
 * Session 模块测试公共 Fixture 工厂（审计 FN-011 整改）。
 *
 * <p>用于在多个测试类（{@code SessionControllerTest} / {@code SessionRepositoryTest}
 * / {@code EndToEndTest} 等）中复用同一套默认值的 Session/Message 构造逻辑，避免
 * 每个测试类各自维护 {@code newSession(String id)} 私有方法造成散落与不一致。</p>
 *
 * <p>设计原则：</p>
 * <ol>
 *   <li>工厂方法以 {@code aXxx} 命名（Builder 风格），返回已填充默认值的可变对象，测试可按需覆盖字段；</li>
 *   <li>不设置 {@code createdAt / updatedAt}：生产环境由 JPA {@code @PrePersist} 自动填充，测试若需校验时间字段应显式赋值；</li>
 *   <li>不依赖 Spring Context，纯 POJO，可在 {@code @BeforeEach} 之外任意使用。</li>
 * </ol>
 *
 * @see <a href="file:///e:/git/Agent-Platform-Prototype/docs/tests/tdd-audit-report-v1.md">docs/tests/tdd-audit-report-v1.md</a> FN-011
 */
public final class SessionFixtures {

    /** 默认租户 ID（与种子数据 infra/sql/mysql/11-seed-data.sql 中 t_001 对应）。 */
    public static final Long DEFAULT_TENANT_ID = 1001L;
    /** 默认用户 ID。 */
    public static final String DEFAULT_USER_ID = "u_001";
    /** 默认 Agent ID（与种子数据 a_2001 对应）。 */
    public static final Long DEFAULT_AGENT_ID = 2001L;

    private SessionFixtures() {
    }

    /**
     * 构造一个默认 ACTIVE 状态的 Session，仅 sessionId 必填，其余字段用约定默认值填充。
     *
     * @param sessionId 会话 ID
     * @return 已填充默认值的 Session 实例（可变，调用方可覆盖字段）
     */
    public static Session aSession(String sessionId) {
        Session s = new Session();
        s.setSessionId(sessionId);
        s.setTenantId(DEFAULT_TENANT_ID);
        s.setUserId(DEFAULT_USER_ID);
        s.setAgentId(DEFAULT_AGENT_ID);
        s.setTitle("测试会话");
        s.setStatus(SessionStatus.ACTIVE.getCode());
        s.setTokenUsed(0L);
        return s;
    }

    /**
     * 构造一个默认 Message，msgId 自动生成（与 sessionId 同前缀，便于排查）。
     *
     * @param sessionId 所属会话 ID
     * @param role      消息角色（USER / ASSISTANT / SYSTEM / TOOL）
     * @param content   消息内容
     * @return 已填充默认值的 Message 实例
     */
    public static Message aMessage(String sessionId, MessageRole role, String content) {
        Message m = new Message();
        m.setMsgId("msg_" + sessionId);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setContentType("text");
        m.setTokenCount(0);
        return m;
    }
}
