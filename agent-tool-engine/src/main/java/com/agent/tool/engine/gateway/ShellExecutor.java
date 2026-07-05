package com.agent.tool.engine.gateway;

import com.agent.tool.engine.api.SandboxBorrower;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.exception.ToolSandboxFailureException;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.sandbox.SandboxExecResult;
import com.agent.tool.engine.sandbox.SandboxInstance;
import com.agent.tool.engine.sandbox.SandboxSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SHELL executor (doc 05-tool-engine §3.1 + §5.3).
 *
 * <p>Borrows a Docker sandbox, runs {@code sh -c <command>} where the command
 * template comes from {@code meta.getEndpoint()}, and pipes
 * {@code request.getInputJson()} as stdin via the {@code TOOL_INPUT_JSON} env var
 * (sandbox shells can read it with {@code echo $TOOL_INPUT_JSON}).</p>
 *
 * <p>Lifecycle: borrow → exec → release (always, even on failure).</p>
 */
@Component
public class ShellExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ShellExecutor.class);

    /** Env var carrying the request JSON payload (sandbox-side reads this). */
    public static final String ENV_INPUT_JSON = "TOOL_INPUT_JSON";

    private final SandboxBorrower sandboxBorrower;

    public ShellExecutor(SandboxBorrower sandboxBorrower) {
        this.sandboxBorrower = sandboxBorrower;
    }

    @Override
    public ExecutorType type() {
        return ExecutorType.SHELL;
    }

    @Override
    public ToolCallResult execute(ToolMeta meta, ToolCallRequest request, long timeoutMs) {
        String command = meta.getEndpoint();
        if (command == null || command.isBlank()) {
            throw new ToolSandboxFailureException(
                    ToolSandboxFailureException.CODE_SANDBOX_FAILURE,
                    "SHELL tool [" + meta.getToolId() + "] missing command endpoint");
        }

        SandboxSpec spec = SandboxSpec.builder()
                .execTimeoutMs(timeoutMs > 0 ? timeoutMs : 60_000L)
                .build();

        SandboxInstance sandbox = sandboxBorrower.borrow(spec);
        String containerId = sandbox.getContainerId();
        log.info("SHELL exec: tool={}, container={}, command={}",
                meta.getToolId(), containerId, command);

        try {
            List<String> argv = List.of("sh", "-c", command);
            Map<String, String> env = Map.of(
                    ENV_INPUT_JSON, request.getInputJson() != null ? request.getInputJson() : "{}",
                    "TOOL_ID", meta.getToolId() != null ? meta.getToolId() : "",
                    "TOOL_TENANT", request.getTenantId() != null ? request.getTenantId() : ""
            );

            SandboxExecResult execResult = sandboxBorrower.exec(
                    containerId, argv, env, timeoutMs > 0 ? timeoutMs : 60_000L);

            return mapToToolCallResult(meta, execResult);
        } finally {
            sandboxBorrower.release(containerId);
        }
    }

    /** Map sandbox exec result to ToolCallResult (SUCCESS when exit 0, TIMEOUT/FAILED otherwise). */
    static ToolCallResult mapToToolCallResult(ToolMeta meta, SandboxExecResult execResult) {
        ToolCallStatus status;
        String errorStack = null;

        if (execResult.isTimedOut()) {
            status = ToolCallStatus.TIMEOUT;
            errorStack = "sandbox exec timed out after " + execResult.getDurationMs() + "ms";
        } else if (execResult.getExitCode() == 0) {
            status = ToolCallStatus.SUCCESS;
        } else {
            status = ToolCallStatus.FAILED;
            errorStack = "exit=" + execResult.getExitCode()
                    + (execResult.getStderr().isBlank() ? "" : " stderr=" + truncate(execResult.getStderr(), 500));
        }

        ToolCallResult result = new ToolCallResult(meta.getToolId(), execResult.getStdout(), status);
        result.setOutputTokens(Math.max(1, execResult.getStdout().length() / 4));
        if (errorStack != null) {
            result.setErrorStack(errorStack);
        }
        return result;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated]";
    }
}
