-- ======================================================================
-- Wave 4.3 - 内容表加乐观锁字段
-- ======================================================================
-- 目的：MyBatis-Plus @Version 注解需要 DB 列配合，update 时自动 WHERE version=? + SET version+1。
-- 防止并发编辑（多人同时改一篇文案，autosave 与手动保存竞态）后写覆盖先写。
-- 现有数据 default=0，未来 update 会逐步累加。
-- ======================================================================

ALTER TABLE `content`
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT 'optimistic lock version (Wave 4.3)';
