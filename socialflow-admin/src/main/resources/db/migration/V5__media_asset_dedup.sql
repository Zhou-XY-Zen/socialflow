-- ======================================================================
-- Wave 4.5 - 媒体素材去重 + 尺寸字段
-- ======================================================================
-- 目的：
--   - sha256：内容去重的 key，上传前先 hash 检查，命中已有素材直接复用
--   - width/height：图片尺寸，前端列表可显示规格 + 适配比例
-- 现有数据 sha256 为空（旧素材不去重），后续新上传逐步填充。
-- ======================================================================

ALTER TABLE `media_asset`
  ADD COLUMN `sha256` CHAR(64)   NULL COMMENT 'SHA-256 hex of file bytes (Wave 4.5 dedup)',
  ADD COLUMN `width`  INT        NULL COMMENT 'image width in pixels (Wave 4.5)',
  ADD COLUMN `height` INT        NULL COMMENT 'image height in pixels (Wave 4.5)';

CREATE INDEX `idx_user_sha256` ON `media_asset` (`user_id`, `sha256`);
