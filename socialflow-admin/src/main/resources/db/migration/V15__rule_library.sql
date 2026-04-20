-- ======================================================================
-- V15: 规约库表（Wave 7）
--      把内存 Holder 持久化到 DB，支持启停 / 用户自定义 / 升级
-- ======================================================================
USE socialflow;

CREATE TABLE `rule_library` (
    `id`             BIGINT       NOT NULL                      COMMENT '主键 (snowflake)',
    `code`           VARCHAR(20)  NOT NULL                      COMMENT '规约编号，如 1.1.1 / 5.1.5',
    `top_category`   VARCHAR(64)  NOT NULL                      COMMENT '一级大类：编程规约/异常日志/单元测试/安全规约/MySQL数据库/工程结构/设计规约',
    `sub_category`   VARCHAR(64)           DEFAULT NULL         COMMENT '二级小节：命名风格/集合处理/...，可空',
    `level`          VARCHAR(16)  NOT NULL                      COMMENT 'MANDATORY / RECOMMENDED / REFERENCE',
    `title`          VARCHAR(512) NOT NULL                      COMMENT '规约首句，UI 卡片显示',
    `body`           TEXT                                       COMMENT '规约主体正文（不含说明/正反例）',
    `description`    TEXT                                       COMMENT '说明',
    `example_good`   TEXT                                       COMMENT '正例',
    `example_bad`    TEXT                                       COMMENT '反例',
    `enabled`        TINYINT UNSIGNED NOT NULL DEFAULT 1        COMMENT '启用：1 启用 / 0 禁用（禁用后审查不报）',
    `is_custom`      TINYINT UNSIGNED NOT NULL DEFAULT 0        COMMENT '是否用户自定义：0 黄山版 / 1 用户加',
    `source`         VARCHAR(64)  NOT NULL DEFAULT 'huangshan-1.7.1' COMMENT '来源：huangshan-1.7.1 / user-custom',
    `create_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`, `is_deleted`),
    KEY `idx_top_sub`  (`top_category`, `sub_category`),
    KEY `idx_level`    (`level`),
    KEY `idx_enabled`  (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阿里巴巴 Java 开发手册（黄山版）规约库 - 支持启停 / 自定义 / 升级';
