-- ======================================================================
-- V16: Finding 加 dismissed_reason 字段（Wave 8 反馈闭环）
--      用户标"无效"时记录原因，后台聚合驱动 prompt 屏蔽列表
-- ======================================================================
USE socialflow;

ALTER TABLE `repo_analysis_finding`
    ADD COLUMN `dismissed_reason` VARCHAR(32) DEFAULT NULL
        COMMENT '关闭原因：INVALID(误判) / ALREADY_FIXED(已修复) / NOT_APPLICABLE(不适用) / OTHER；
        与 status=IGNORED/RESOLVED 配合使用'
        AFTER `resolution_note`;

-- 加个索引：按 ruleRef + dismissed_reason 聚合统计 INVALID 次数排行用
ALTER TABLE `repo_analysis_finding`
    ADD INDEX `idx_dismissed_rule` (`dismissed_reason`, `rule_ref`(64));
