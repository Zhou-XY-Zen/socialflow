-- ======================================================================
-- V14: 给 llm_call_log 表补齐黄山版 5.1.5 强制要求的两个字段
--      update_time + is_deleted（V12 当初建表漏了）
-- ======================================================================
USE socialflow;

ALTER TABLE `llm_call_log`
    ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间' AFTER `create_time`,
    ADD COLUMN `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '逻辑删除：0 未删 / 1 已删' AFTER `update_time`;
