-- ======================================================================
-- V13: 拓宽 repo_analysis_finding 几个文本字段
-- ----------------------------------------------------------------------
-- 背景：接入黄山版 321 条规约后，LLM 输出的 ruleRef 越来越规范
--       (如 "黄山版 5.1.5 - 表必备三字段 (id, create_time, update_time)")
--       原 VARCHAR(128) 装不下，导致 INSERT 时 Data truncation 异常
-- ======================================================================
USE socialflow;

-- rule_ref：LLM 引用的规约条款，预留足够空间
ALTER TABLE `repo_analysis_finding`
    MODIFY COLUMN `rule_ref` VARCHAR(255) DEFAULT NULL COMMENT '阿里规约条款引用，如「黄山版 X.Y.Z 标题」';

-- category：分类名（"安全规约/并发处理/MySQL 索引"等），原默认 64 改 96 留余量
ALTER TABLE `repo_analysis_finding`
    MODIFY COLUMN `category` VARCHAR(96) DEFAULT NULL COMMENT '类别：安全/并发/命名/SQL/异常等';

-- line_range：原 64 一般够，但 LLM 偶尔输出 "42-46, 78, 102-115" 多段，给到 128
ALTER TABLE `repo_analysis_finding`
    MODIFY COLUMN `line_range` VARCHAR(128) DEFAULT NULL COMMENT '行范围，可能多段如 42-46, 78';
