-- =====================================================================
-- File: 12-outbox-message.sql
-- Domain: common (outbox pattern for cross-service write compensation)
-- Source: docs/plans/10-cross-service-compensation-and-exception-plan.md (S-04)
-- Pattern: Local Message Table (Outbox) — each service creates this table
--           in its own database. OutboxRelay polls PENDING and publishes
--           to RocketMQ for eventual consistency.
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

-- NOTE: Each service should create this table in its own DB.
-- Replace {service_db} with the target database name.
-- Example: USE agent_tool; CREATE TABLE outbox_message ...

-- ---------------------------------------------------------------------
-- Table: outbox_message  (Outbox pattern, S-04 Plan Phase 1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `outbox_message` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT    COMMENT 'Primary key, auto-increment',
    `aggregate_id`    VARCHAR(64)      NOT NULL                   COMMENT 'Business aggregate ID (e.g., tool call ID, agent instance ID)',
    `topic`           VARCHAR(128)     NOT NULL                   COMMENT 'RocketMQ topic (e.g., tool.audit, runtime.stepstate)',
    `payload`         TEXT             NOT NULL                   COMMENT 'Message payload (JSON string)',
    `status`          VARCHAR(16)      NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED / DEAD',
    `retry_count`     INT UNSIGNED     NOT NULL DEFAULT 0         COMMENT 'Number of publish attempts',
    `next_retry_at`   DATETIME(3)      NOT NULL                   COMMENT 'Next retry timestamp (for exponential backoff)',
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation timestamp',
    `sent_at`         DATETIME(3)      NULL                       COMMENT 'Timestamp when successfully published',
    PRIMARY KEY (`id`),
    KEY `idx_outbox_status_next_retry` (`status`, `next_retry_at`),
    KEY `idx_outbox_aggregate` (`aggregate_id`),
    KEY `idx_outbox_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox message table (S-04 cross-service compensation)';
