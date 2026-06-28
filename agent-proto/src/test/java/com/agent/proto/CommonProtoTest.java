package com.agent.proto;

import agentplatform.common.v1.TraceContext;
import agentplatform.common.v1.Error;
import agentplatform.common.v1.Pagination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonProtoTest {

    @Test
    @DisplayName("TraceContext 序列化后所有字段应可往返还原")
    void should_RoundTripAllFields_When_TraceContextSerialized() throws Exception {
        TraceContext ctx = TraceContext.newBuilder()
                .setTenantId(1001L)
                .setUserId("u_123")
                .setSessionId("ss_a1b2c3d4")
                .setTaskId("tk_yyy")
                .setSubtaskId("st_001")
                .setTraceId("trace-abc")
                .setSpanId("span-def")
                .build();

        TraceContext parsed = TraceContext.parseFrom(ctx.toByteArray());
        assertThat(parsed.getTenantId()).isEqualTo(1001L);
        assertThat(parsed.getUserId()).isEqualTo("u_123");
        assertThat(parsed.getSessionId()).isEqualTo("ss_a1b2c3d4");
        assertThat(parsed.getTaskId()).isEqualTo("tk_yyy");
        assertThat(parsed.getSubtaskId()).isEqualTo("st_001");
        assertThat(parsed.getTraceId()).isEqualTo("trace-abc");
        assertThat(parsed.getSpanId()).isEqualTo("span-def");
    }

    @Test
    @DisplayName("Error 应携带 code、message 与 details")
    void should_CarryCodeAndMessageAndDetails_When_ErrorSerialized() throws Exception {
        Error err = Error.newBuilder()
                .setCode("TASK_NOT_FOUND")
                .setMessage("任务不存在")
                .setDetails("{\"taskId\":\"tk_xxx\"}")
                .build();
        Error parsed = Error.parseFrom(err.toByteArray());
        assertThat(parsed.getCode()).isEqualTo("TASK_NOT_FOUND");
        assertThat(parsed.getMessage()).isEqualTo("任务不存在");
        assertThat(parsed.getDetails()).isEqualTo("{\"taskId\":\"tk_xxx\"}");
    }

    @Test
    @DisplayName("Pagination 应持有 page、size 与 total")
    void should_HoldPageAndSizeAndTotal_When_PaginationSerialized() throws Exception {
        Pagination p = Pagination.newBuilder()
                .setPage(1)
                .setSize(20)
                .setTotal(135L)
                .build();
        Pagination parsed = Pagination.parseFrom(p.toByteArray());
        assertThat(parsed.getPage()).isEqualTo(1);
        assertThat(parsed.getSize()).isEqualTo(20);
        assertThat(parsed.getTotal()).isEqualTo(135L);
    }
}
