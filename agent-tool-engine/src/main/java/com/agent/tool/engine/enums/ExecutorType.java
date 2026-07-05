package com.agent.tool.engine.enums;

/**
 * Tool executor type (doc 05-tool-engine §3.1 + §3.2).
 *
 * <p>Four execution strategies aligned with doc 05 §3.1:
 * <ul>
 *   <li>{@code HTTP_API}: call external HTTP/REST API (R1 read-only or R2 write-local)</li>
 *   <li>{@code SHELL}: execute shell command inside Docker sandbox (R3)</li>
 *   <li>{@code PYTHON}: execute Python script inside Docker sandbox (R3)</li>
 *   <li>{@code MCP}: Model Context Protocol delegatee (placeholder, T12 completes)</li>
 * </ul>
 * </p>
 *
 * <p>Risk routing:
 * <ul>
 *   <li>R1 (NONE/READ_ONLY) → HTTP_API or MCP, no sandbox</li>
 *   <li>R2 (WRITE_LOCAL) → HTTP_API, no sandbox</li>
 *   <li>R3 (WRITE_EXTERNAL/DESTRUCTIVE) → SHELL or PYTHON, sandbox required</li>
 * </ul>
 * </p>
 */
public enum ExecutorType {
    HTTP_API,
    SHELL,
    PYTHON,
    MCP
}
