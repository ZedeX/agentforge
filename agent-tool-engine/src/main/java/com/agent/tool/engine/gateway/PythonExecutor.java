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
 * PYTHON executor (doc 05-tool-engine §3.1 + §5.3).
 *
 * <p>Borrows a Docker sandbox, runs {@code python <script>} where the script
 * path comes from {@code meta.getEndpoint()} (e.g. {@code /workspace/weather.py}),
 * and passes {@code request.getInputJson()} via the {@code TOOL_INPUT_JSON} env var
 * for the script to read with {@code os.environ['TOOL_INPUT_JSON']}.</p>
 *
 * <p>Lifecycle: borrow → exec → release (always, even on failure).</p>
 */
@Component
public class PythonExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutor.class);

    /** Env var carrying the request JSON payload (sandbox-side reads this). */
    public static final String ENV_INPUT_JSON = "TOOL_INPUT_JSON";

    private final SandboxBorrower sandboxBorrower;

    public PythonExecutor(SandboxBorrower sandboxBorrower) {
        this.sandboxBorrower = sandboxBorrower;
    }

    @Override
    public ExecutorType type() {
        return ExecutorType.PYTHON;
    }

    @Override
    public ToolCallResult execute(ToolMeta meta, ToolCallRequest request, long timeoutMs) {
        String scriptPath = meta.getEndpoint();
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new ToolSandboxFailureException(
                    ToolSandboxFailureException.CODE_SANDBOX_FAILURE,
                    "PYTHON tool [" + meta.getToolId() + "] missing script endpoint");
        }

        SandboxSpec spec = SandboxSpec.builder()
                .execTimeoutMs(timeoutMs > 0 ? timeoutMs : 60_000L)
                .build();

        SandboxInstance sandbox = sandboxBorrower.borrow(spec);
        String containerId = sandbox.getContainerId();
        log.info("PYTHON exec: tool={}, container={}, script={}",
                meta.getToolId(), containerId, scriptPath);

        try {
            List<String> argv = List.of("python", scriptPath);
            Map<String, String> env = Map.of(
                    ENV_INPUT_JSON, request.getInputJson() != null ? request.getInputJson() : "{}",
                    "TOOL_ID", meta.getToolId() != null ? meta.getToolId() : "",
                    "TOOL_TENANT", request.getTenantId() != null ? request.getTenantId() : "",
                    "PYTHONUNBUFFERED", "1"
            );

            SandboxExecResult execResult = sandboxBorrower.exec(
                    containerId, argv, env, timeoutMs > 0 ? timeoutMs : 60_000L);

            return ShellExecutor.mapToToolCallResult(meta, execResult);
        } finally {
            sandboxBorrower.release(containerId);
        }
    }
}
