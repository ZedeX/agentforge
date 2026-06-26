-- =====================================================================
-- File: 07-agent-knowledge.sql
-- Domain: agent_knowledge (knowledge-service)
-- Source: docs/01-database/database-schema-design.md §7
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_knowledge
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_knowledge;

-- ---------------------------------------------------------------------
-- Table: knowledge_base  (知识库表, doc §7.1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`                 BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `kb_id`              VARCHAR(32)      NOT NULL                COMMENT '知识库业务 ID',
    `name`               VARCHAR(128)    NOT NULL                COMMENT '名称',
    `domain`             VARCHAR(64)      NOT NULL                COMMENT '业务域',
    `description`        TEXT             NULL                    COMMENT '描述',
    `milvus_collection`  VARCHAR(64)      NOT NULL                COMMENT 'Milvus Collection 名',
    `embedding_model`    VARCHAR(64)      NOT NULL                COMMENT '向量化模型',
    `chunk_strategy`     JSON             NOT NULL                COMMENT '切片策略',
    `status`             TINYINT          NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    `created_at`         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`         VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`         VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`            TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`            INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_id` (`kb_id`),
    KEY `idx_base_id` (`kb_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ---------------------------------------------------------------------
-- Table: knowledge_document  (知识文档表, doc §7.2)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `doc_id`        VARCHAR(32)      NOT NULL                COMMENT '文档 ID',
    `kb_id`         VARCHAR(32)      NOT NULL                COMMENT '知识库',
    `title`         VARCHAR(255)    NOT NULL                COMMENT '标题',
    `source_type`   VARCHAR(16)      NOT NULL                COMMENT 'file/url/manual',
    `source_url`    VARCHAR(512)    NULL                    COMMENT '来源 URL',
    `object_key`    VARCHAR(255)    NULL                    COMMENT 'MinIO 对象 Key',
    `file_type`     VARCHAR(16)      NULL                    COMMENT 'pdf/docx/md/html',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1      COMMENT '版本',
    `chunk_count`   INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '切片数',
    `status`        VARCHAR(16)      NOT NULL                COMMENT 'pending/parsing/ready/failed',
    `parsed_at`     DATETIME(3)      NULL                    COMMENT '解析完成时间',
    `acl`           JSON             NOT NULL                COMMENT '权限控制 (用户/角色可见性)',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version_lock`  INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_id` (`doc_id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

-- ---------------------------------------------------------------------
-- Table: knowledge_chunk  (知识切片表, doc §7.3)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `chunk_id`      VARCHAR(32)      NOT NULL                COMMENT '切片 ID',
    `doc_id`        VARCHAR(32)      NOT NULL                COMMENT '文档 ID',
    `kb_id`         VARCHAR(32)      NOT NULL                COMMENT '知识库',
    `seq_no`        INT UNSIGNED     NOT NULL                COMMENT '文档内序号',
    `content`       TEXT             NOT NULL                COMMENT '切片内容',
    `vector_id`     VARCHAR(64)      NOT NULL                COMMENT 'Milvus 主键',
    `metadata`      JSON             NOT NULL                COMMENT '元数据 (页码/标题/章节)',
    `quality_score` DECIMAL(4,3)     NULL                    COMMENT '质量评分',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chunk_id` (`chunk_id`),
    KEY `idx_base_id` (`kb_id`),
    KEY `idx_doc_seq` (`doc_id`, `seq_no`),
    KEY `idx_status` (`deleted`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识切片表';
