-- ======================================================================
-- Wave 5 - 代码分析工具：存放项目分析与提交审查结果
-- ======================================================================
USE socialflow;
SET NAMES utf8mb4;

-- 分析主表：承载"项目概览 / 提交审查 / 对比分析"三种类型的结果
CREATE TABLE `repo_analysis` (
  `id`              BIGINT       NOT NULL COMMENT '主键 (snowflake)',
  `user_id`         BIGINT       NOT NULL COMMENT '发起用户',
  `git_url`         VARCHAR(512) NOT NULL COMMENT '仓库 Git URL',
  `branch`          VARCHAR(128)          DEFAULT 'main' COMMENT '分支名',
  `commit_sha`      CHAR(40)              DEFAULT NULL COMMENT '提交审查对应 SHA；项目概览为 NULL',
  `base_ref`        VARCHAR(128)          DEFAULT NULL COMMENT '对比分析 base 引用',
  `head_ref`        VARCHAR(128)          DEFAULT NULL COMMENT '对比分析 head 引用',
  `analysis_type`   VARCHAR(32)  NOT NULL COMMENT 'PROJECT_OVERVIEW / COMMIT_REVIEW / DIFF_REVIEW',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  `stage`           VARCHAR(64)           DEFAULT NULL COMMENT '当前阶段（克隆/扫描/分析/渲染）',
  `progress_percent` INT                  DEFAULT 0 COMMENT '0-100 进度百分比',
  `progress_message` VARCHAR(512)         DEFAULT NULL COMMENT '进度描述文字',
  `overall_score`   INT                   DEFAULT NULL COMMENT '0-100 综合评分（仅审查类）',
  `high_count`      INT                   DEFAULT 0,
  `medium_count`    INT                   DEFAULT 0,
  `low_count`       INT                   DEFAULT 0,
  `summary_md`      MEDIUMTEXT                    COMMENT '总结 Markdown',
  `tech_stack_json` TEXT                          COMMENT '技术栈数组 JSON（项目概览用）',
  `language_stats_json` TEXT                      COMMENT '语言行数占比 JSON',
  `mermaid_code`    TEXT                          COMMENT 'LLM 生成的流程图 Mermaid 代码',
  `is_favorite`     TINYINT               DEFAULT 0 COMMENT '是否收藏',
  `share_token`     VARCHAR(64)           DEFAULT NULL COMMENT '免登录分享 token',
  `tags`            VARCHAR(256)          DEFAULT NULL COMMENT '用户标签，逗号分隔',
  `error_msg`       VARCHAR(1024)         DEFAULT NULL,
  `duration_ms`     BIGINT                DEFAULT NULL,
  `llm_tokens_used` INT                   DEFAULT NULL,
  `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT               DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_token` (`share_token`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_repo_type` (`git_url`(191), `analysis_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码分析结果表';

-- 审查发现明细：单条 finding 可独立标注为"已修复/忽略/待跟进"
CREATE TABLE `repo_analysis_finding` (
  `id`              BIGINT       NOT NULL COMMENT '主键 (snowflake)',
  `analysis_id`     BIGINT       NOT NULL COMMENT '关联 repo_analysis.id',
  `level`           VARCHAR(8)   NOT NULL COMMENT 'HIGH / MEDIUM / LOW',
  `category`        VARCHAR(64)           DEFAULT NULL COMMENT '类别：安全/并发/命名/SQL/异常等',
  `title`           VARCHAR(256) NOT NULL COMMENT '一句话概括',
  `file`            VARCHAR(512)          DEFAULT NULL,
  `line_range`      VARCHAR(64)           DEFAULT NULL,
  `description`     TEXT                           COMMENT '问题详细描述',
  `suggestion`      TEXT                           COMMENT '修复建议',
  `code_snippet`    TEXT                           COMMENT '相关代码片段',
  `rule_ref`        VARCHAR(128)          DEFAULT NULL COMMENT '阿里规约条款引用',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'UNRESOLVED' COMMENT 'UNRESOLVED/RESOLVED/IGNORED',
  `resolution_note` VARCHAR(512)          DEFAULT NULL COMMENT '用户备注',
  `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT               DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_analysis` (`analysis_id`, `level`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码审查发现明细';

-- 仓库书签：用户常用仓库收藏
CREATE TABLE `repo_bookmark` (
  `id`            BIGINT       NOT NULL,
  `user_id`       BIGINT       NOT NULL,
  `nickname`      VARCHAR(128) NOT NULL COMMENT '自定义昵称',
  `git_url`       VARCHAR(512) NOT NULL,
  `branch`        VARCHAR(128)          DEFAULT 'main',
  `tags`          VARCHAR(256)          DEFAULT NULL,
  `last_analyzed_at` DATETIME           DEFAULT NULL,
  `last_score`    INT                   DEFAULT NULL,
  `create_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT               DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_url` (`user_id`, `git_url`(191), `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库收藏';
