package com.agent.proto;

import agentplatform.common.v1.TraceContext;
import agentplatform.common.v1.Error;
import agentplatform.common.v1.Pagination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonProtoTest {

    @Test
    void traceContext_canRoundTripAllFields() throws Exception {
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
        assertEquals(1001L, parsed.getTenantId());
        assertEquals("u_123", parsed.getUserId());
        assertEquals("ss_a1b2c3d4", parsed.getSessionId());
        assertEquals("tk_yyy", parsed.getTaskId());
        assertEquals("st_001", parsed.getSubtaskId());
        assertEquals("trace-abc", parsed.getTraceId());
        assertEquals("span-def", parsed.getSpanId());
    }

    @Test
    void error_carriesCodeAndMessageAndDetails() throws Exception {
        Error err = Error.newBuilder()
                .setCode("TASK_NOT_FOUND")
                .setMessage("任务不存在")
                .setDetails("{\"taskId\":\"tk_xxx\"}")
                .build();
        Error parsed = Error.parseFrom(err.toByteArray());
        assertEquals("TASK_NOT_FOUND", parsed.getCode());
        assertEquals("任务不存在", parsed.getMessage());
        assertEquals("{\"taskId\":\"tk_xxx\"}", parsed.getDetails());
    }

    @Test
    void pagination_holdsPageAndSizeAndTotal() throws Exception {
        Pagination p = Pagination.newBuilder()
                .setPage(1)
                .setSize(20)
                .setTotal(135L)
                .build();
        Pagination parsed = Pagination.parseFrom(p.toByteArray());
        assertEquals(1, parsed.getPage());
        assertEquals(20, parsed.getSize());
        assertEquals(135L, parsed.getTotal());
    }
}
