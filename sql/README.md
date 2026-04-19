# sql/ 目录说明

**MySQL schema 的权威来源是 Flyway 迁移脚本**：
`socialflow-admin/src/main/resources/db/migration/V*.sql`。
新增表 / 字段 / 索引请添加新的 `V{n}__xxx.sql`，**不要**改这里。

本目录下保留的脚本：

| 文件 | 用途 |
|------|------|
| `init-data.sql` | 早期通用模板（1001-1004），尚未迁入 Flyway；新环境可手动执行一次。建议后续整合为 `V7__seed_extra_templates.sql` 并删除此文件 |
| `pgvector-init.sql` | PostgreSQL + pgvector 作为向量存储时的可选初始化脚本；使用 Milvus 时可忽略 |

之前的 `schema.sql` / `seed-templates.sql` 与 Flyway `V1__init_schema.sql` / `V2__seed_templates.sql` 完全重复，已删除。
