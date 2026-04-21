-- V19 升级代码分析相关大字段容量
--
-- 原 TEXT（64 KB）在以下场景会溢出：
--   - repo_analysis.mermaid_code  —— 大型系统流程图（1000+ 节点/连线）可能 100KB+
--   - repo_analysis_finding.code_snippet —— 大函数或多行上下文可能超 64KB
--
-- 升级到 MEDIUMTEXT（16 MB）足够大部分场景；未来若仍不够可再升 LONGTEXT（4 GB）。
ALTER TABLE repo_analysis
    MODIFY COLUMN mermaid_code MEDIUMTEXT NULL COMMENT '核心流程图 Mermaid 源码（可大）';

ALTER TABLE repo_analysis_finding
    MODIFY COLUMN code_snippet MEDIUMTEXT NULL COMMENT 'Finding 引用的代码片段（可大）';
