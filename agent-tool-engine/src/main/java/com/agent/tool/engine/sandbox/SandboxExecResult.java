package com.agent.tool.engine.sandbox;

import java.io.Serializable;

/**
 * Result of executing a command inside a sandbox container (doc 05 §5.3).
 *
 * <p>Captures stdout, stderr, exit code, wall-clock duration, and a
 * {@code timedOut} flag set when the command exceeded the exec timeout
 * and the container was killed.</p>
 */
public final class SandboxExecResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final long durationMs;
    private final boolean timedOut;

    public SandboxExecResult(String stdout, String stderr, int exitCode,
                             long durationMs, boolean timedOut) {
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
        this.timedOut = timedOut;
    }

    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public int getExitCode() { return exitCode; }
    public long getDurationMs() { return durationMs; }
    public boolean isTimedOut() { return timedOut; }

    /** Convenience: exit code 0 and not timed out. */
    public boolean isSuccessful() {
        return !timedOut && exitCode == 0;
    }

    @Override
    public String toString() {
        return "SandboxExecResult{exitCode=" + exitCode
                + ", durationMs=" + durationMs
                + ", timedOut=" + timedOut
                + ", stdoutLen=" + stdout.length()
                + ", stderrLen=" + stderr.length() + '}';
    }
}
