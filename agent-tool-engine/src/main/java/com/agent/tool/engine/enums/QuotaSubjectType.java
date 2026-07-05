package com.agent.tool.engine.enums;

/**
 * Quota subject type (doc 01-database §4.3 tool_quota.subject_type).
 *
 * <p>TENANT=租户级, AGENT=Agent 级, TASK=任务级.</p>
 */
public enum QuotaSubjectType {

    TENANT,
    AGENT,
    TASK
}
