package com.agent.tool.engine.api;

/**
 * Sandbox borrower port (F8 R3 exec: container borrow + recycle).
 *
 * <p>R3 tools execute in a disposable container: borrow() creates, recycle() does docker rm.</p>
 */
public interface SandboxBorrower {

    /**
     * Borrow a fresh sandbox container.
     *
     * @return sandboxId
     */
    String borrow();

    /**
     * Recycle (docker rm) a sandbox container after use.
     */
    void recycle(String sandboxId);
}
