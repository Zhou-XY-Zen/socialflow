-- ======================================================================
-- Wave 5.2 - 代码分析 · 凭证加 auth_type 字段（TOKEN / PASSWORD 两种方式）
-- ======================================================================
USE socialflow;

ALTER TABLE `repo_auth_credential`
  ADD COLUMN `auth_type` VARCHAR(16) NOT NULL DEFAULT 'TOKEN'
    COMMENT 'TOKEN=PAT/个人访问令牌（推荐）; PASSWORD=账号密码'
  AFTER `git_host`;
