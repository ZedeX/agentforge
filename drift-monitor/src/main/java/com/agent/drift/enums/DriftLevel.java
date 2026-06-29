package com.agent.drift.enums;

/**
 * Drift severity level (F11 correction routing).
 */
public enum DriftLevel {

    /** Session-level: inject core constraint summary. */
    SESSION,
    /** System-level: rollback to previous stable version. */
    SYSTEM
}
