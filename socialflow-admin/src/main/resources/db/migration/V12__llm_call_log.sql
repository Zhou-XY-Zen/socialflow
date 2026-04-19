-- ======================================================================
-- Wave 5.5 - LLM 调用明细日志
--   全量分析模式下一次分析会多次调用 LLM（按模块/按文件）
--   每次调用都记录一条，用于：
--     1. 分析详情页展示调用链路
--     2. 仪表盘统计用户本月 token 消耗
-- ======================================================================
USE socialflow;
SET NAMES utf8mb4;

CREATE TABLE `llm_call_log` (
  `id`               BIGINT       NOT NULL,
  `analysis_id`      BIGINT       NOT NULL COMMENT '关联 repo_analysis.id',
  `user_id`          BIGINT       NOT NULL,
  `stage`            VARCHAR(64)  NOT NULL COMMENT '阶段标识：MODULE_SUMMARY_xxx / FILE_REVIEW_xxx / FINAL 等',
  `stage_label`      VARCHAR(256)          DEFAULT NULL COMMENT '人类可读描述',
  `provider`         VARCHAR(32)  NOT NULL,
  `model`            VARCHAR(64)  NOT NULL,
  `prompt_tokens`    INT                   DEFAULT 0,
  `completion_tokens` INT                  DEFAULT 0,
  `total_tokens`     INT                   DEFAULT 0,
  `latency_ms`       BIGINT                DEFAULT NULL,
  `success`          TINYINT      NOT NULL DEFAULT 1,
  `error_msg`        VARCHAR(512)          DEFAULT NULL,
  `create_time`      DATETIME              DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_analysis` (`analysis_id`, `create_time`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 调用明细日志';
