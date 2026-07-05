package com.agent.tool.engine.enums;

/**
 * Tool side effect classification (doc 05-tool-engine §3.2).
 *
 * <p>Five-level taxonomy aligned with doc 05 §3.2 + §4.2 risk grading:</p>
 * <ul>
 *   <li>NONE: no side effect (pure read / computation)</li>
 *   <li>READ_ONLY: read external state but no mutation (e.g. HTTP GET)</li>
 *   <li>WRITE_LOCAL: mutate local state (file system / db / cache)</li>
 *   <li>WRITE_EXTERNAL: mutate external system state (HTTP POST/PUT/DELETE, send email)</li>
 *   <li>DESTRUCTIVE: irreversible destructive operations (drop table, delete files, format disk)</li>
 * </ul>
 */
public enum SideEffect {

    NONE,
    READ_ONLY,
    WRITE_LOCAL,
    WRITE_EXTERNAL,
    DESTRUCTIVE
}
