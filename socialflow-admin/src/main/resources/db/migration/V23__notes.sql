-- ============================================================
-- V23__notes.sql —— 知识中枢（个人博客 / 学习笔记）模块
-- ============================================================
-- 配套模块：socialflow-service-note
-- 主要能力：
--   1) 笔记 CRUD + 分类 + 标签 + 软删 + 版本
--   2) 文件上传流水线（任务 + 子项 + AI 富化产物）
--   3) 双向链接（显式 [[link]] + 语义自动建边）
--   4) 公开博客（slug + 访问统计预留）
-- ============================================================

-- 笔记主表
CREATE TABLE note (
    id            BIGINT       NOT NULL COMMENT '主键 ID',
    user_id       BIGINT       NOT NULL COMMENT '所属用户',
    title         VARCHAR(200) NOT NULL COMMENT '标题',
    content_md    MEDIUMTEXT   NOT NULL COMMENT 'Markdown 原文',
    summary       VARCHAR(500) DEFAULT NULL COMMENT '摘要（列表页展示，AI 富化或手填）',
    ai_outline    JSON         DEFAULT NULL COMMENT 'AI 抽取的大纲（heading 列表）',
    category_id   BIGINT       DEFAULT NULL COMMENT '所属分类（note_category.id）',
    word_count    INT          NOT NULL DEFAULT 0 COMMENT '字数统计（去 markdown 标记）',
    read_score    INT          DEFAULT NULL COMMENT 'Optimizer 给的可读性 0~100',
    is_pinned     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    is_public     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否公开（公开博客）',
    slug          VARCHAR(120) DEFAULT NULL COMMENT '公开博客访问 slug（user_id+slug 唯一）',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=草稿 3=回收站',
    source_type   VARCHAR(20)  NOT NULL DEFAULT 'manual' COMMENT '来源：manual/upload/url/clip',
    source_ref    VARCHAR(500) DEFAULT NULL COMMENT '来源引用（原文件名 / URL / import_item_id）',
    published_at  DATETIME     DEFAULT NULL COMMENT '首次设为公开的时间',
    create_time   DATETIME     NOT NULL,
    update_time   DATETIME     NOT NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_status_updated (user_id, status, update_time),
    KEY idx_user_category (user_id, category_id),
    KEY idx_user_pinned (user_id, is_pinned),
    UNIQUE KEY uk_user_slug (user_id, slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记';

-- 分类（最多两级；parent_id=NULL 即顶级）
CREATE TABLE note_category (
    id           BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    parent_id    BIGINT      DEFAULT NULL,
    name         VARCHAR(50) NOT NULL,
    sort_order   INT         NOT NULL DEFAULT 0,
    color        VARCHAR(20) DEFAULT NULL COMMENT '前端色块（可空）',
    create_time  DATETIME    NOT NULL,
    update_time  DATETIME    NOT NULL,
    is_deleted   TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_parent (user_id, parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记分类';

-- 标签（按 user 隔离 + 名字唯一）
CREATE TABLE note_tag (
    id           BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    name         VARCHAR(50) NOT NULL,
    usage_count  INT         NOT NULL DEFAULT 0 COMMENT '关联笔记数（懒维护）',
    create_time  DATETIME    NOT NULL,
    update_time  DATETIME    NOT NULL,
    is_deleted   TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (user_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记标签';

-- 笔记 ↔ 标签（关联表）
CREATE TABLE note_tag_rel (
    note_id      BIGINT   NOT NULL,
    tag_id       BIGINT   NOT NULL,
    create_time  DATETIME NOT NULL,
    PRIMARY KEY (note_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记标签关联';

-- 双向链接（[[explicit]] + semantic 自动连边）
CREATE TABLE note_link (
    id            BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL COMMENT '冗余 user_id 便于按用户查图',
    src_note_id   BIGINT      NOT NULL,
    dst_note_id   BIGINT      NOT NULL,
    link_type     VARCHAR(16) NOT NULL COMMENT 'explicit / semantic',
    similarity    FLOAT       DEFAULT NULL COMMENT '语义边的余弦相似度',
    create_time   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pair_type (src_note_id, dst_note_id, link_type),
    KEY idx_user (user_id),
    KEY idx_dst (dst_note_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记链接（图谱边）';

-- 笔记历史版本（自动保存 / 冲突合并）
CREATE TABLE note_version (
    id                     BIGINT     NOT NULL,
    note_id                BIGINT     NOT NULL,
    version                INT        NOT NULL,
    title                  VARCHAR(200) NOT NULL,
    content_md             MEDIUMTEXT NOT NULL,
    change_summary         VARCHAR(200) DEFAULT NULL,
    source_import_item_id  BIGINT     DEFAULT NULL,
    create_time            DATETIME   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_version (note_id, version),
    KEY idx_note (note_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记版本快照';

-- 导入任务（一次拖拽 = 一个 task）
CREATE TABLE note_import_task (
    id              BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    source_type     VARCHAR(20)  NOT NULL COMMENT 'file / zip / folder / url / vault',
    source_name     VARCHAR(255) DEFAULT NULL COMMENT '展示用：第一个文件名 / ZIP 名 / URL',
    total_files     INT          NOT NULL DEFAULT 0,
    processed_files INT          NOT NULL DEFAULT 0,
    failed_files    INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending'
        COMMENT 'pending/running/reviewing/committed/failed/cancelled',
    enrich_enabled  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否走 AI 富化',
    error_msg       TEXT         DEFAULT NULL,
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    finished_at     DATETIME     DEFAULT NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_user_create (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记导入任务';

-- 导入子项（任务里每个文件一行）
CREATE TABLE note_import_item (
    id                     BIGINT       NOT NULL,
    task_id                BIGINT       NOT NULL,
    user_id                BIGINT       NOT NULL,
    file_path              VARCHAR(500) NOT NULL COMMENT '相对路径（含 zip 内层级）',
    file_name              VARCHAR(255) NOT NULL,
    file_size              BIGINT       NOT NULL DEFAULT 0,
    file_hash              CHAR(64)     DEFAULT NULL COMMENT 'SHA-256（L1 去重）',
    content_hash           CHAR(64)     DEFAULT NULL COMMENT '正文归一化哈希（L2 去重）',
    parse_status           VARCHAR(16)  NOT NULL DEFAULT 'pending'
        COMMENT 'pending/running/done/failed/skipped',
    enrich_status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
    parsed_md              MEDIUMTEXT   DEFAULT NULL COMMENT '解析后的 Markdown（commit 后清空）',
    parsed_title           VARCHAR(200) DEFAULT NULL COMMENT '解析时识别的标题',
    ai_payload             JSON         DEFAULT NULL
        COMMENT '富化中间产物：{summary, tags[], categoryGuess, outline, related[]}',
    conflict_with_note_id  BIGINT       DEFAULT NULL COMMENT '检测到的冲突笔记',
    resolution             VARCHAR(16)  NOT NULL DEFAULT 'pending'
        COMMENT 'pending/skip/create/overwrite/merge',
    final_note_id          BIGINT       DEFAULT NULL COMMENT '入库后的 note.id',
    error_msg              TEXT         DEFAULT NULL,
    retry_count            INT          NOT NULL DEFAULT 0,
    create_time            DATETIME     NOT NULL,
    update_time            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_task (task_id),
    KEY idx_user (user_id),
    KEY idx_file_hash (file_hash),
    KEY idx_content_hash (content_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '笔记导入子项';

-- 公开博客访问日志（轻量；后续接 Dashboard 看板）
CREATE TABLE note_blog_view_log (
    id           BIGINT       NOT NULL,
    note_id      BIGINT       NOT NULL,
    slug         VARCHAR(120) NOT NULL,
    ip_hash      CHAR(64)     DEFAULT NULL COMMENT 'IP 哈希后存（去 PII）',
    referer      VARCHAR(500) DEFAULT NULL,
    user_agent   VARCHAR(500) DEFAULT NULL,
    create_time  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_note (note_id),
    KEY idx_slug_time (slug, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '公开博客访问日志';
