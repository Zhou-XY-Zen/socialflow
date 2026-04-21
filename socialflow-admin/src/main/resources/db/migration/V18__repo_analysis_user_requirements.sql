-- V18 repo_analysis 新增 user_requirements 字段
--
-- 用户提交分析时可输入"自定义分析诉求"，让 LLM 按此重点输出。字段较大（TEXT），
-- 也便于审查结果页回显给查看者。
-- Flyway 保证只执行一次。
ALTER TABLE repo_analysis
    ADD COLUMN user_requirements TEXT NULL COMMENT '用户自定义分析诉求；LLM 按此重点输出';
