-- ======================================================================
-- Wave 4.2 - 图像生成去重缓存
-- ======================================================================
-- 目的：同 prompt+model+size 不再重复调用 DashScope wanx，节省 API 成本和时间。
-- 命中规则：SHA-256(prompt + ':' + model + ':' + size) 作为 prompt_hash，
--          按 (user_id, prompt_hash) 唯一索引去重。
-- 失效策略：30 天后自然过期（应用层定期清理或手动删除）。
-- ======================================================================

CREATE TABLE IF NOT EXISTS `image_asset_cache` (
  `id`            BIGINT       NOT NULL                COMMENT 'PK (snowflake)',
  `user_id`       BIGINT       NOT NULL                COMMENT 'FK sys_user.id',
  `prompt_hash`   CHAR(64)     NOT NULL                COMMENT 'SHA-256 hex of prompt+model+size',
  `media_ids`     JSON         NOT NULL                COMMENT 'JSON array of MediaAsset.id (1-4 items)',
  `prompt`        TEXT         NULL                    COMMENT 'original prompt for debug/audit',
  `model`         VARCHAR(64)  NULL                    COMMENT 'wanx model name',
  `image_size`    VARCHAR(32)  NULL                    COMMENT '1024*1024 etc.',
  `hit_count`     INT          NOT NULL DEFAULT 1      COMMENT 'times this cache was hit',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_prompt_hash` (`user_id`, `prompt_hash`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI image generation dedup cache';
