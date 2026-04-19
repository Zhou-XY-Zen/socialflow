-- ======================================================================
-- Wave 5.3 - 凭证加"默认仓库 URL"
--   用途：
--     1. 测试连接时用这个 URL 做真实 ls-remote（比 host root 更准）
--     2. 前端卡片展示"此凭证常用来访问 xxx 仓库"
--     3. 卡片上"用此凭证分析"快捷按钮跳转到项目概览并预填
-- ======================================================================
USE socialflow;

ALTER TABLE `repo_auth_credential`
  ADD COLUMN `default_repo_url` VARCHAR(512) DEFAULT NULL
    COMMENT '常用仓库 URL（可选）：用于测试连接 + 快速发起分析'
  AFTER `token_hint`;
