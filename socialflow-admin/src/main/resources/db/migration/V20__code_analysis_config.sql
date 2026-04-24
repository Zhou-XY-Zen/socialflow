-- V20: 代码分析模块的用户级 LLM 配置
-- 每用户一条，优先级高于 application.yml 的 socialflow.code-analysis.*。
-- 未配置（表里没记录）时回退到 yml 默认值（当前 deepseek-v4-pro）。

CREATE TABLE code_analysis_config (
    id              BIGINT          NOT NULL COMMENT '雪花 ID',
    user_id         BIGINT          NOT NULL COMMENT '用户 ID',
    provider        VARCHAR(32)     NOT NULL COMMENT 'LLM Provider: DEEPSEEK / QWEN / GLM / OPENAI / CLAUDE',
    model           VARCHAR(64)     NOT NULL COMMENT '模型名：deepseek-v4-pro / deepseek-v4-flash / ...',
    temperature     DECIMAL(3, 2)   DEFAULT 0.30 COMMENT '采样温度 0.00-1.00',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码分析用户级 LLM 配置';
