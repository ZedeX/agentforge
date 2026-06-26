-- =====================================================================
-- File: 09-agent-risk.sql
-- Domain: agent_risk (risk-control)
-- Source: docs/01-database/database-schema-design.md §9.2 permission_policy
-- Note: role / role_permission 为 DBA 要求的标准 RBAC 扩展表
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_risk
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_risk;

-- ---------------------------------------------------------------------
-- Table: permission_policy  (权限策略表 RBAC+ABAC, doc §9.2)
-- 含 subject_type / resource_type / action 字段
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission_policy` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `policy_id`     VARCHAR(32)      NOT NULL                COMMENT '策略 ID',
    `subject_type`  VARCHAR(16)      NOT NULL                COMMENT 'role=角色 user=用户 agent',
    `subject_id`    VARCHAR(64)      NOT NULL                COMMENT '主体',
    `resource_type` VARCHAR(32)     NOT NULL                COMMENT 'tool/knowledge/agent',
    `resource_id`   VARCHAR(64)      NOT NULL                COMMENT '资源',
    `action`        VARCHAR(16)      NOT NULL                COMMENT 'read/write/execute',
    `effect`        VARCHAR(8)       NOT NULL                COMMENT 'allow/deny',
    `conditions`    JSON             NULL                    COMMENT 'ABAC 条件 (时间/IP/参数)',
    `expire_at`     DATETIME(3)      NULL                    COMMENT '临时权限过期时间',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_policy_id` (`policy_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`),
    KEY `idx_resource` (`resource_type`, `resource_id`),
    KEY `idx_action` (`action`),
    KEY `idx_status` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限策略表 (RBAC+ABAC)';

-- ---------------------------------------------------------------------
-- Table: role  (角色表, RBAC 扩展)
-- 初始化: admin / developer / operator / user 4 角色
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `role_id`       VARCHAR(32)      NOT NULL                COMMENT '角色业务 ID',
    `role_code`     VARCHAR(32)      NOT NULL                COMMENT '角色编码 (admin/developer/operator/user)',
    `name`          VARCHAR(64)      NOT NULL                COMMENT '角色名称',
    `description`   VARCHAR(255)    NULL                    COMMENT '角色描述',
    `permissions`   JSON             NULL                    COMMENT '权限摘要 (冗余, 详细见 role_permission)',
    `status`        TINYINT          NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_id` (`role_id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ---------------------------------------------------------------------
-- Table: role_permission  (角色权限关联表, RBAC 扩展)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`             BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `role_id`        VARCHAR(32)      NOT NULL                COMMENT '角色 ID',
    `policy_id`      VARCHAR(32)      NOT NULL                COMMENT '权限策略 ID',
    `effect`         VARCHAR(8)       NOT NULL DEFAULT 'allow' COMMENT 'allow/deny',
    `created_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`     VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`     VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`        TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`        INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_policy` (`role_id`, `policy_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_policy_id` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';
