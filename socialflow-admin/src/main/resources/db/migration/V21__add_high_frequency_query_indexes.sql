-- ======================================================================
-- V21 — 为高频查询补充复合索引
-- ======================================================================
-- 背景：阶段 2 的项目分析发现以下查询走全表扫描或仅命中前缀索引：
--   1. 「我的内容」分页：WHERE user_id=? ORDER BY create_time DESC
--      原 idx_user_status / idx_user_platform 都覆盖了 user_id 但 ORDER BY 落到 filesort
--   2. 「平台聚合统计」：WHERE platform=? ORDER BY create_time DESC（如全量趋势分析）
--   3. 内容关键字搜索：WHERE body LIKE '%word%' 走全表
--   4. 「我的发布任务」：WHERE content_id IN (我的所有 content) ORDER BY create_time DESC
--   5. 评测结果列表：WHERE eval_task_id=? ORDER BY model_run（已有 idx_task，验证）
-- ======================================================================
-- 兼容性：
--   - MySQL 8.0+ 支持 DESC 索引（5.7 也接受语法但实际正向存储），项目最低版本 MySQL 8.0
--   - 不删除已有索引（保留现网行为兼容）；MySQL 优化器会选最优索引
--   - 全文索引使用 ngram 分词器，适配中文分词
-- ======================================================================

-- ---------- content 表 ----------

-- 用户内容列表分页：覆盖 user_id 过滤 + create_time 排序
CREATE INDEX `idx_user_create` ON `content` (`user_id`, `create_time` DESC);

-- 跨用户的平台聚合（仪表板趋势 / 平台数据）
CREATE INDEX `idx_platform_create` ON `content` (`platform`, `create_time` DESC);

-- 关键词搜索：取代当前 LIKE '%kw%' 全表扫
-- ngram 分词适配中文，最小 token 长度需在配置 ngram_token_size=2（默认即 2）
ALTER TABLE `content`
  ADD FULLTEXT INDEX `ft_title_body` (`title`, `body`) /*!50700 WITH PARSER ngram */;


-- ---------- publish_task 表 ----------
-- 已有 idx_status_scheduled 和 idx_content；补一个 (status, create_time DESC) 用于"待执行队列"扫表
CREATE INDEX `idx_status_create` ON `publish_task` (`status`, `create_time` DESC);


-- ---------- guardrail_log 表 ----------
-- 已有 (user_id, create_time)；补 (rule_name, create_time DESC) 用于"按规则统计触发频次"
CREATE INDEX `idx_rule_create` ON `guardrail_log` (`rule_name`, `create_time` DESC);


-- ---------- prompt_template 表 ----------
-- 缺少基于 is_system + sort_order 的查找索引
-- PromptServiceImpl.loadTemplate 按 platform + is_system=1 + sort_order ASC 查询默认模板
CREATE INDEX `idx_platform_system_sort`
  ON `prompt_template` (`platform`, `is_system`, `sort_order`);
