-- ======================================================================
-- Wave 5.4 - 凭证下挂多个仓库（一对多）
--   语义：凭证 = 父，Git 仓库地址 = 子
--   原 repo_auth_credential.default_repo_url 改为若干"项目"
-- ======================================================================
USE socialflow;
SET NAMES utf8mb4;

CREATE TABLE `repo_credential_project` (
  `id`             BIGINT       NOT NULL,
  `credential_id`  BIGINT       NOT NULL COMMENT '父凭证 id',
  `nickname`       VARCHAR(128)          DEFAULT NULL COMMENT '仓库昵称（可留空，默认取仓库名）',
  `git_url`        VARCHAR(512) NOT NULL COMMENT '完整 Git URL',
  `branch`         VARCHAR(128) NOT NULL DEFAULT 'main',
  `last_used_at`   DATETIME              DEFAULT NULL,
  `create_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cred` (`credential_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证 ↔ Git 仓库 一对多';

-- 兼容迁移：把旧的 default_repo_url 搬到子表（如果有值的话）
INSERT INTO repo_credential_project (id, credential_id, nickname, git_url, branch, create_time, is_deleted)
SELECT
  FLOOR(RAND() * 9000000000000000000) + 1000000000000000000 AS id,  -- 粗略唯一 id；JPA 层将改用 snowflake
  id AS credential_id,
  NULL AS nickname,
  default_repo_url AS git_url,
  'main' AS branch,
  create_time,
  0
FROM repo_auth_credential
WHERE default_repo_url IS NOT NULL AND default_repo_url <> '' AND is_deleted = 0;
