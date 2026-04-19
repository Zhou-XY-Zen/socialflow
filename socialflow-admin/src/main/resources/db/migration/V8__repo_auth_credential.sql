-- ======================================================================
-- Wave 5.1 - 代码分析 · 仓库凭证管理
-- ======================================================================
USE socialflow;
SET NAMES utf8mb4;

-- 用户的 Git 仓库访问凭证（按 git_host 匹配）
-- token 列存 AES-256-GCM 加密密文，不存明文
CREATE TABLE `repo_auth_credential` (
  `id`              BIGINT       NOT NULL,
  `user_id`         BIGINT       NOT NULL,
  `nickname`        VARCHAR(128) NOT NULL COMMENT '用户自定义昵称，如 "我的 GitHub 个人"',
  `git_host`        VARCHAR(256) NOT NULL COMMENT 'Git Host，如 github.com / gitee.com / gitlab.company.com / 10.0.0.5:3000',
  `username`        VARCHAR(128) NOT NULL COMMENT 'Git 用户名',
  `token_encrypted` VARCHAR(512) NOT NULL COMMENT 'Personal Access Token（AES-GCM Base64）',
  `token_hint`      VARCHAR(32)           DEFAULT NULL COMMENT 'token 掩码展示，如 ghp_****f8a',
  `is_default`      TINYINT      NOT NULL DEFAULT 0 COMMENT '同一 host 下的默认凭证',
  `last_used_at`    DATETIME              DEFAULT NULL,
  `test_status`     VARCHAR(16)           DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/SUCCESS/FAILED',
  `test_message`    VARCHAR(256)          DEFAULT NULL COMMENT '测试连接的错误/成功信息',
  `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_host` (`user_id`, `git_host`),
  KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码分析 · Git 仓库访问凭证';
