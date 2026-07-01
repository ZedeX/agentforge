-- =====================================================================
-- File: 07-agent-knowledge.sql
-- Domain: agent_knowledge (knowledge-service, port 8098)
-- Source: docs/01-database/database-schema-design.md §7 + Plan 08 T8
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
--
-- Wave 24 (Plan 08 T8) refactor: 对齐 4 个 JPA Entity (KnowledgeBase /
--   KnowledgeDocument / DocumentChunk / KnowledgeVersion). 主键统一为
--   BIGINT id (IDENTITY), 业务键加 UNIQUE 约束, createdAt/updatedAt 用
--   BIGINT (epoch millis) 对齐 POJO 字段类型.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_knowledge
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_knowledge;

-- ---------------------------------------------------------------------
-- Table: knowledge_base  (知识库表, doc §7.1, KnowledgeBase Entity)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `kb_id`           VARCHAR(32)     NOT NULL                 COMMENT '知识库业务 ID',
    `name`            VARCHAR(128)    NOT NULL                 COMMENT '名称',
    `description`     TEXT            NULL                     COMMENT '描述',
    `doc_count`       INT             NOT NULL DEFAULT 0       COMMENT '文档数',
    `chunk_count`     INT             NOT NULL DEFAULT 0       COMMENT '切片数',
    `embedding_model` VARCHAR(64)     NOT NULL DEFAULT 'bge-large-zh-v1.5' COMMENT '向量化模型',
    `dimension`       INT             NOT NULL DEFAULT 1024    COMMENT '向量维度',
    `status`          VARCHAR(16)     NOT NULL DEFAULT 'CREATING' COMMENT 'CREATING/READY/UPDATING/ERROR/DELETED',
    `created_at`      BIGINT          NOT NULL                 COMMENT '创建时间 (epoch millis)',
    `updated_at`      BIGINT          NOT NULL                 COMMENT '更新时间 (epoch millis)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_id` (`kb_id`),
    KEY `idx_kb_status` (`status`),
    KEY `idx_kb_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ---------------------------------------------------------------------
-- Table: knowledge_document  (知识文档表, KnowledgeDocument Entity)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `doc_id`        VARCHAR(32)     NOT NULL                 COMMENT '文档 ID',
    `kb_id`         VARCHAR(32)     NOT NULL                 COMMENT '知识库 ID',
    `name`          VARCHAR(255)    NOT NULL                 COMMENT '文档名',
    `type`          VARCHAR(16)     NOT NULL DEFAULT 'TEXT'  COMMENT 'TEXT/MARKDOWN/HTML/PDF/UNKNOWN',
    `content`       MEDIUMTEXT      NULL                     COMMENT '文档原始内容',
    `size_bytes`    INT             NOT NULL DEFAULT 0       COMMENT '内容字节大小',
    `chunk_count`   INT             NOT NULL DEFAULT 0       COMMENT '切片数',
    `token_count`   INT             NOT NULL DEFAULT 0       COMMENT 'Token 总数',
    `created_at`    BIGINT          NOT NULL                 COMMENT '创建时间 (epoch millis)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_id` (`doc_id`),
    KEY `idx_doc_kb_id` (`kb_id`),
    KEY `idx_doc_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

-- ---------------------------------------------------------------------
-- Table: knowledge_chunk  (知识切片表, DocumentChunk Entity)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `chunk_id`      VARCHAR(32)     NOT NULL                 COMMENT '切片 ID',
    `doc_id`        VARCHAR(32)     NOT NULL                 COMMENT '文档 ID',
    `kb_id`         VARCHAR(32)     NOT NULL                 COMMENT '知识库 ID',
    `content`       TEXT            NOT NULL                 COMMENT '切片内容',
    `token_count`   INT             NOT NULL DEFAULT 0       COMMENT 'Token 数',
    `order_index`   INT             NOT NULL DEFAULT 0       COMMENT '文档内序号',
    `embedding_id`  VARCHAR(128)    NULL                     COMMENT 'Milvus 主键 (向量化后填充)',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/VECTORIZED/FAILED',
    `created_at`    BIGINT          NOT NULL                 COMMENT '创建时间 (epoch millis)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chunk_id` (`chunk_id`),
    KEY `idx_chunk_kb_doc` (`kb_id`, `doc_id`),
    KEY `idx_chunk_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识切片表';

-- ---------------------------------------------------------------------
-- Table: knowledge_version  (知识库版本快照表, KnowledgeVersion Entity)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_version` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `version_id`    VARCHAR(32)     NOT NULL                 COMMENT '版本 ID',
    `kb_id`         VARCHAR(32)     NOT NULL                 COMMENT '知识库 ID',
    `version`       INT             NOT NULL                 COMMENT '版本号',
    `snapshot`      MEDIUMTEXT      NOT NULL                 COMMENT 'KnowledgeBase 快照 JSON',
    `change_log`    TEXT            NULL                     COMMENT '变更说明',
    `created_at`    BIGINT          NOT NULL                 COMMENT '创建时间 (epoch millis)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_id` (`version_id`),
    KEY `idx_version_kb_version` (`kb_id`, `version`),
    KEY `idx_version_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库版本快照表';
