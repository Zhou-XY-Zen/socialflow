-- =====================================================================
-- SocialFlow - Database schema (MySQL 8.0+)
-- Charset: utf8mb4, Collation: utf8mb4_general_ci
-- All tables use snowflake IDs and include common fields:
--   id (BIGINT PK), create_time, update_time, is_deleted
-- =====================================================================


SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------
-- 2.1 sys_user  user account
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`             BIGINT         NOT NULL                 COMMENT 'PK (snowflake)',
  `email`          VARCHAR(128)   NOT NULL                 COMMENT 'login email',
  `password_hash`  VARCHAR(256)   NOT NULL                 COMMENT 'BCrypt hash',
  `nickname`       VARCHAR(64)    NOT NULL                 COMMENT 'nickname',
  `avatar_url`     VARCHAR(512)   NULL                     COMMENT 'avatar url',
  `status`         TINYINT        NOT NULL DEFAULT 1       COMMENT '0=disabled 1=active',
  `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT        NOT NULL DEFAULT 0       COMMENT 'logical delete',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user';

-- ----------------------------------------------------------------------
-- 2.2 user_api_key  provider API keys
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `user_api_key`;
CREATE TABLE `user_api_key` (
  `id`                 BIGINT      NOT NULL,
  `user_id`            BIGINT      NOT NULL                COMMENT 'FK sys_user.id',
  `provider`           VARCHAR(32) NOT NULL                COMMENT 'deepseek/qwen/openai/claude',
  `api_key_encrypted`  TEXT        NOT NULL                COMMENT 'AES-256-GCM encrypted',
  `base_url`           VARCHAR(256) NULL                   COMMENT 'custom API endpoint',
  `is_default`         TINYINT     NOT NULL DEFAULT 0      COMMENT 'default provider flag',
  `create_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`         TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_provider` (`user_id`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user api key config';

-- ----------------------------------------------------------------------
-- 2.3 prompt_template  Prompt templates
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `prompt_template`;
CREATE TABLE `prompt_template` (
  `id`                   BIGINT      NOT NULL,
  `template_name`        VARCHAR(128) NOT NULL,
  `platform`             VARCHAR(32) NOT NULL              COMMENT 'XIAOHONGSHU/DOUYIN/WECHAT_MOMENT/WECHAT_MP',
  `category`             VARCHAR(32) NOT NULL              COMMENT 'SEED/TUTORIAL/STORY/PROMO/GENERAL',
  `system_prompt`        TEXT        NOT NULL,
  `user_prompt_template` TEXT        NOT NULL              COMMENT 'with {{variable}} placeholders',
  `variables`            JSON        NULL                  COMMENT 'variable definitions',
  `few_shot_examples`    JSON        NULL                  COMMENT 'few-shot examples',
  `output_format`        VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/JSON/MARKDOWN',
  `is_system`            TINYINT     NOT NULL DEFAULT 0    COMMENT 'system preset',
  `user_id`              BIGINT      NULL                  COMMENT 'creator (null for system templates)',
  `sort_order`           INT         NOT NULL DEFAULT 0,
  `create_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_platform_category` (`platform`, `category`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='prompt template';

-- ----------------------------------------------------------------------
-- 2.4 content  copywriting content
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `content`;
CREATE TABLE `content` (
  `id`                 BIGINT       NOT NULL,
  `user_id`            BIGINT       NOT NULL             COMMENT 'FK sys_user.id',
  `title`              VARCHAR(256) NULL,
  `body`               MEDIUMTEXT   NOT NULL             COMMENT 'markdown body',
  `platform`           VARCHAR(32)  NOT NULL,
  `status`             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SCHEDULED/PUBLISHING/PUBLISHED/FAILED',
  `scheduled_time`     DATETIME     NULL,
  `published_time`     DATETIME     NULL,
  `published_url`      VARCHAR(512) NULL,
  `template_id`        BIGINT       NULL                 COMMENT 'FK prompt_template.id',
  `generation_params`  JSON         NULL                 COMMENT 'topic/keywords/model snapshot',
  `tags`               VARCHAR(512) NULL                 COMMENT 'comma-separated tags',
  `ai_model`           VARCHAR(64)  NULL,
  `token_usage`        INT          NULL,
  `kb_id`              BIGINT       NULL                 COMMENT 'knowledge base used (RAG)',
  `eval_score`         DECIMAL(3,1) NULL                 COMMENT '1.0 - 5.0',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_user_platform` (`user_id`, `platform`),
  KEY `idx_scheduled_time` (`scheduled_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='copywriting content';

-- ----------------------------------------------------------------------
-- 2.5 content_version  history of a content row
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `content_version`;
CREATE TABLE `content_version` (
  `id`             BIGINT       NOT NULL,
  `content_id`     BIGINT       NOT NULL             COMMENT 'FK content.id',
  `version_num`    INT          NOT NULL,
  `body_snapshot`  MEDIUMTEXT   NOT NULL,
  `change_desc`    VARCHAR(256) NULL                 COMMENT 'AI_GENERATE/MANUAL_EDIT/REWRITE/AGENT_OPTIMIZE',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_content_version` (`content_id`, `version_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='content version history';

-- ----------------------------------------------------------------------
-- 2.6 media_asset  media library
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `media_asset`;
CREATE TABLE `media_asset` (
  `id`             BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL,
  `file_name`      VARCHAR(256) NOT NULL,
  `file_type`      VARCHAR(16)  NOT NULL             COMMENT 'IMAGE/VIDEO',
  `mime_type`      VARCHAR(64)  NOT NULL,
  `file_url`       VARCHAR(512) NOT NULL,
  `thumbnail_url`  VARCHAR(512) NULL,
  `file_size`      BIGINT       NOT NULL,
  `tags`           VARCHAR(512) NULL,
  `vector_id`      VARCHAR(128) NULL                 COMMENT 'vector DB id for semantic search',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_type` (`user_id`, `file_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='media asset';

-- ----------------------------------------------------------------------
-- 2.7 content_media_rel
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `content_media_rel`;
CREATE TABLE `content_media_rel` (
  `content_id`  BIGINT NOT NULL,
  `media_id`    BIGINT NOT NULL,
  `sort_order`  INT    NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`content_id`, `media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='content-media relation';

-- ----------------------------------------------------------------------
-- 2.8 platform_account  third-party platform credentials
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `platform_account`;
CREATE TABLE `platform_account` (
  `id`                BIGINT       NOT NULL,
  `user_id`           BIGINT       NOT NULL,
  `platform`          VARCHAR(32)  NOT NULL,
  `account_name`      VARCHAR(128) NOT NULL,
  `app_id`            VARCHAR(128) NULL,
  `access_token`      TEXT         NULL             COMMENT 'AES encrypted',
  `refresh_token`     TEXT         NULL             COMMENT 'AES encrypted',
  `token_expires_at`  DATETIME     NULL,
  `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '0=unauthorized 1=authorized 2=expired',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_platform` (`user_id`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='platform account';

-- ----------------------------------------------------------------------
-- 2.9 publish_task
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `publish_task`;
CREATE TABLE `publish_task` (
  `id`                   BIGINT      NOT NULL,
  `content_id`           BIGINT      NOT NULL,
  `platform_account_id`  BIGINT      NULL,
  `publish_type`         VARCHAR(16) NOT NULL           COMMENT 'AUTO/MANUAL',
  `status`               VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTING/SUCCESS/FAILED/CANCELLED',
  `scheduled_time`       DATETIME    NULL,
  `executed_time`        DATETIME    NULL,
  `result_msg`           TEXT        NULL,
  `retry_count`          INT         NOT NULL DEFAULT 0,
  `create_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_scheduled` (`status`, `scheduled_time`),
  KEY `idx_content` (`content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='publish task';

-- ----------------------------------------------------------------------
-- 2.10 knowledge_base
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base` (
  `id`               BIGINT       NOT NULL,
  `user_id`          BIGINT       NOT NULL,
  `name`             VARCHAR(128) NOT NULL,
  `description`      VARCHAR(512) NULL,
  `category`         VARCHAR(64)  NULL            COMMENT 'beauty/tech/food/general',
  `doc_count`        INT          NOT NULL DEFAULT 0,
  `chunk_count`      INT          NOT NULL DEFAULT 0,
  `embedding_model`  VARCHAR(64)  NOT NULL DEFAULT 'bge-m3',
  `embedding_dim`    INT          NOT NULL DEFAULT 1024,
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled 1=active',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='knowledge base';

-- ----------------------------------------------------------------------
-- 2.11 knowledge_document
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_document`;
CREATE TABLE `knowledge_document` (
  `id`            BIGINT       NOT NULL,
  `kb_id`         BIGINT       NOT NULL,
  `file_name`     VARCHAR(256) NOT NULL,
  `file_url`      VARCHAR(512) NOT NULL,
  `file_type`     VARCHAR(16)  NOT NULL           COMMENT 'PDF/DOCX/TXT/HTML/MD',
  `file_size`     BIGINT       NOT NULL,
  `char_count`    INT          NULL,
  `chunk_count`   INT          NOT NULL DEFAULT 0,
  `parse_status`  VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/COMPLETED/FAILED',
  `parse_error`   VARCHAR(512) NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='knowledge document';

-- ----------------------------------------------------------------------
-- 2.12 knowledge_chunk  chunk metadata (vectors stored in vector DB)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_chunk`;
CREATE TABLE `knowledge_chunk` (
  `id`            BIGINT       NOT NULL,
  `doc_id`        BIGINT       NOT NULL,
  `kb_id`         BIGINT       NOT NULL             COMMENT 'redundant for fast filter',
  `chunk_index`   INT          NOT NULL             COMMENT 'order within doc',
  `content_text`  TEXT         NOT NULL,
  `token_count`   INT          NOT NULL,
  `vector_id`     VARCHAR(128) NOT NULL             COMMENT 'vector DB id',
  `metadata`      JSON         NULL                 COMMENT '{page, section, source}',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb_doc` (`kb_id`, `doc_id`),
  KEY `idx_vector_id` (`vector_id`),
  FULLTEXT KEY `ft_content` (`content_text`) /*!50700 WITH PARSER ngram */
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='knowledge chunk metadata';

-- ----------------------------------------------------------------------
-- 2.13 ai_usage_log
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_usage_log`;
CREATE TABLE `ai_usage_log` (
  `id`                 BIGINT        NOT NULL,
  `user_id`            BIGINT        NOT NULL,
  `model`              VARCHAR(64)   NOT NULL,
  `provider`           VARCHAR(32)   NOT NULL,
  `prompt_tokens`      INT           NOT NULL,
  `completion_tokens`  INT           NOT NULL,
  `total_tokens`       INT           NOT NULL,
  `cost_estimate`      DECIMAL(10,6) NULL             COMMENT 'CNY estimate',
  `request_type`       VARCHAR(32)   NOT NULL         COMMENT 'GENERATE/REWRITE/TITLE/HASHTAG/GUARDRAIL/EVAL/RAG_QUERY/AGENT',
  `content_id`         BIGINT        NULL,
  `latency_ms`         INT           NULL,
  `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`         TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_request_type` (`request_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI usage log';

-- ----------------------------------------------------------------------
-- 2.14 guardrail_log
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `guardrail_log`;
CREATE TABLE `guardrail_log` (
  `id`             BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL,
  `rule_name`      VARCHAR(64)  NOT NULL             COMMENT 'PROMPT_INJECTION/SENSITIVE_WORD/FORMAT_CHECK/HALLUCINATION',
  `trigger_type`   VARCHAR(8)   NOT NULL             COMMENT 'INPUT/OUTPUT',
  `input_text`     TEXT         NULL,
  `output_text`    TEXT         NULL,
  `reason`         VARCHAR(512) NOT NULL,
  `action_taken`   VARCHAR(32)  NOT NULL             COMMENT 'BLOCKED/REGENERATED/WARNING',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_rule_name` (`rule_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='guardrail trigger log';

-- ----------------------------------------------------------------------
-- 2.15 eval_task
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `eval_task`;
CREATE TABLE `eval_task` (
  `id`               BIGINT       NOT NULL,
  `user_id`          BIGINT       NOT NULL,
  `name`             VARCHAR(128) NOT NULL,
  `config_a`         JSON         NOT NULL           COMMENT '{model, templateId, temperature}',
  `config_b`         JSON         NOT NULL,
  `test_topics`      JSON         NOT NULL           COMMENT '[{topic, platform, keywords}]',
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
  `total_cases`      INT          NOT NULL,
  `completed_cases`  INT          NOT NULL DEFAULT 0,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='eval task';

-- ----------------------------------------------------------------------
-- 2.16 eval_result
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `eval_result`;
CREATE TABLE `eval_result` (
  `id`               BIGINT       NOT NULL,
  `eval_task_id`     BIGINT       NOT NULL,
  `input_topic`      VARCHAR(512) NOT NULL,
  `input_platform`   VARCHAR(32)  NOT NULL,
  `output_a`         TEXT         NOT NULL,
  `output_b`         TEXT         NOT NULL,
  `scores_a`         JSON         NOT NULL           COMMENT 'per-dimension scores',
  `scores_b`         JSON         NOT NULL,
  `total_score_a`    DECIMAL(3,1) NOT NULL,
  `total_score_b`    DECIMAL(3,1) NOT NULL,
  `winner`           VARCHAR(8)   NOT NULL           COMMENT 'A/B/TIE',
  `judge_reasoning`  TEXT         NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`eval_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='eval result';

-- ----------------------------------------------------------------------
-- 2.17 user_preference  long-term user profile
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `user_preference`;
CREATE TABLE `user_preference` (
  `id`                  BIGINT       NOT NULL,
  `user_id`             BIGINT       NOT NULL,
  `default_platform`    VARCHAR(32)  NULL,
  `default_tone`        VARCHAR(32)  NULL             COMMENT 'casual/professional/humorous/inspiring',
  `preferred_keywords`  VARCHAR(512) NULL,
  `style_notes`         TEXT         NULL,
  `history_summary`     TEXT         NULL             COMMENT 'auto-summarized by LLM',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user preference / long-term memory';

-- ----------------------------------------------------------------------
-- 2.18 conversation_session  short-term session store
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `conversation_session`;
CREATE TABLE `conversation_session` (
  `id`             BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL,
  `session_type`   VARCHAR(32)  NOT NULL             COMMENT 'GENERATE/REWRITE/CHAT',
  `messages`       JSON         NOT NULL             COMMENT '[{role, content, timestamp}]',
  `summary`        TEXT         NULL,
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='conversation session';

SET FOREIGN_KEY_CHECKS = 1;
