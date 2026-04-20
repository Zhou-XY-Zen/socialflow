-- V17 代码分析模块：安全加固 + 仪表盘聚合索引
--
-- 变更点：
--   1) repo_analysis 新增 share_expire_at / share_access_count
--      配合服务端 7 天 TTL + 10000 次访问上限，防暴力枚举分享 token
--   2) 清理 UNRESOLVED 状态却带 dismissed_reason 的脏数据（状态机约束）
--   3) 为仪表盘聚合加复合索引（替代原"拉 500 条内存聚合"）
--
-- Flyway 保证只执行一次；MySQL 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，
-- 因此这里直接 ALTER / CREATE，重复执行需回滚 flyway_schema_history 后重新发版。

-- 1) repo_analysis：分享 token 过期 + 访问计数
ALTER TABLE repo_analysis
    ADD COLUMN share_expire_at    DATETIME NULL            COMMENT '分享 token 过期时刻；NULL 表示从未生成',
    ADD COLUMN share_access_count INT      NOT NULL DEFAULT 0 COMMENT '分享 token 累计访问次数';

-- 已生成但无过期时间的历史记录，给 7 天宽限期，避免突然全量失效
UPDATE repo_analysis
SET share_expire_at = DATE_ADD(NOW(), INTERVAL 7 DAY)
WHERE share_token IS NOT NULL AND share_expire_at IS NULL;

-- 2) 清理 finding 状态机不一致的历史脏数据
UPDATE repo_analysis_finding
SET dismissed_reason = NULL
WHERE status = 'UNRESOLVED' AND dismissed_reason IS NOT NULL;

-- 3) 仪表盘聚合用的复合索引
CREATE INDEX idx_repo_analysis_user_ctime  ON repo_analysis          (user_id, create_time);
CREATE INDEX idx_repo_analysis_user_giturl ON repo_analysis          (user_id, git_url);
CREATE INDEX idx_finding_analysis_status   ON repo_analysis_finding  (analysis_id, status);
CREATE INDEX idx_llm_call_user_ctime       ON llm_call_log           (user_id, create_time);
