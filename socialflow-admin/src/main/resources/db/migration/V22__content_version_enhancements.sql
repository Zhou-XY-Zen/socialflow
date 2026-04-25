-- ======================================================================
-- V22 — content_version 表增强：精细化变更追踪
-- ======================================================================
-- 背景：content_version 当前只快照了 body_snapshot 和一句简短的 change_desc。
-- 这导致：
--   1. 无法回溯 title / tags 的变化历史（用户改了标题但 body 不变时，版本号会跳但内容看似一致）
--   2. 无法精细 diff（不知道这次改了哪几个字段）
--   3. 看一个版本时不知道当时的 status 是什么（已发布版还是草稿？）
--
-- 本次迁移补充：
--   - title_snapshot：标题快照
--   - tags_snapshot：标签快照
--   - status_snapshot：状态快照
--   - changed_fields：以逗号分隔的字段名列表（"title,body" / "tags"）
--   - change_summary：变更摘要 JSON（每个字段的 before/after 长度差，可选）
-- ======================================================================
-- 兼容性：所有新字段都允许 NULL，老版本记录不会被破坏；
-- ContentPersister 在写入新版本时会同时填这些字段（见配套代码改动）。
-- ======================================================================

ALTER TABLE `content_version`
  ADD COLUMN `title_snapshot`  VARCHAR(256) NULL                     COMMENT '版本写入时的 title 快照',
  ADD COLUMN `tags_snapshot`   VARCHAR(512) NULL                     COMMENT '版本写入时的 tags 快照',
  ADD COLUMN `status_snapshot` VARCHAR(20)  NULL                     COMMENT '版本写入时的 status 快照',
  ADD COLUMN `changed_fields`  VARCHAR(256) NULL                     COMMENT '本次变更涉及的字段名（逗号分隔）',
  ADD COLUMN `change_summary`  JSON         NULL                     COMMENT '可选：每个字段的 before/after 摘要';
