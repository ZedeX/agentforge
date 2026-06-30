package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.model.ToolCallAuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolCallAuditorImpl} 单元测试。
 */
class ToolCallAuditorImplTest {

    private final ToolCallAuditorImpl auditor = new ToolCallAuditorImpl();

    @Test
    @DisplayName("audit 正常日志: 写入成功且 count 递增")
    void should_WriteLog_When_Audit() {
        ToolCallAuditLog logEntry = new ToolCallAuditLog("trace_001", "tool_ok", ToolCallStatus.SUCCESS);
        logEntry.setInputJson("{\"q\":\"test\"}");
        logEntry.setOutput("result");

        auditor.audit(logEntry);

        assertThat(auditor.count()).isEqualTo(1);
        assertThat(logEntry.getLogId()).isNotBlank();
    }

    @Test
    @DisplayName("logId 缺失时: 自动生成 log-N")
    void should_GenerateLogId_When_Missing() {
        ToolCallAuditLog logEntry = new ToolCallAuditLog("trace_002", "tool_x", ToolCallStatus.SUCCESS);

        auditor.audit(logEntry);

        assertThat(logEntry.getLogId()).startsWith("log-");
        assertThat(logEntry.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("audit null 日志: 安全跳过, count 不变")
    void should_Skip_When_NullLog() {
        auditor.audit(null);

        assertThat(auditor.count()).isZero();
    }

    @Test
    @DisplayName("多条审计日志: allLogs 返回全部并按写入顺序")
    void should_ReturnAllLogs_When_Queried() {
        auditor.audit(new ToolCallAuditLog("t1", "tool_a", ToolCallStatus.SUCCESS));
        auditor.audit(new ToolCallAuditLog("t2", "tool_b", ToolCallStatus.FAILED));
        auditor.audit(new ToolCallAuditLog("t3", "tool_c", ToolCallStatus.TIMEOUT));

        List<ToolCallAuditLog> all = auditor.allLogs();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).getTraceId()).isEqualTo("t1");
        assertThat(all.get(1).getTraceId()).isEqualTo("t2");
        assertThat(all.get(2).getStatus()).isEqualTo(ToolCallStatus.TIMEOUT);
    }

    @Test
    @DisplayName("失败日志含错误堆栈: 完整保留")
    void should_PreserveErrorStack_When_FailedLog() {
        ToolCallAuditLog failLog = new ToolCallAuditLog("trace_err", "tool_fail", ToolCallStatus.FAILED);
        failLog.setErrorStack("NullPointerException@ToolGateway.invoke:42");

        auditor.audit(failLog);

        ToolCallAuditLog stored = auditor.allLogs().get(0);
        assertThat(stored.getErrorStack()).contains("NullPointerException");
        assertThat(stored.getStatus()).isEqualTo(ToolCallStatus.FAILED);
    }
}
