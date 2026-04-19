# SocialFlow

> AI-powered social media content operation platform — an end-to-end practice project
> that exercises RAG, vector search, knowledge bases, AI Guardrails, Skills/Tools,
> Multi-Agent workflows, LLM-as-Judge evaluation, SSE streaming, and memory management.

**Version:** 1.0.0-SNAPSHOT  •  **Stack:** Spring Boot 3.3 + LangChain4j + Vue 3 + MySQL + pgvector + Redis + 腾讯云 COS  •  **JDK:** 21+

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│  Vue 3 + Element Plus  (socialflow-ui, :5173)                   │
└─────────────────────────────────────────────────────────────────┘
                         │  /api/v1   SSE
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot 3.3  (socialflow-admin, :8080)                     │
│    web → service → dao → model → common                         │
│                                                                 │
│    service.ai                                                   │
│    ├── llm          (provider router: deepseek/qwen/openai/claude)│
│    ├── prompt       (template render)                           │
│    ├── rag          (HyDE + hybrid + rerank pipeline)           │
│    ├── embedding    (BGE-M3 + vector store abstraction)         │
│    ├── guardrails   (responsibility chain: 9 rules)             │
│    ├── agent        (Planner → Writer → Reviewer → Optimizer)   │
│    ├── eval         (LLM-as-Judge A/B evaluation)               │
│    ├── memory       (session + long-term)                       │
│    └── tools        (Skills / Function Calling)                 │
└─────────────────────────────────────────────────────────────────┘
                         │
       ┌─────────┬───────┼────────┬──────────┐
       ▼         ▼       ▼        ▼          ▼
    MySQL     Redis    pgvector   (COS on cloud)    LLM APIs
    :3306    :6379    :9000    :5432
```

## Module Layout

```
socialflow/
├── pom.xml                           # parent (dependency management)
├── docker-compose.yml                # local infra (MySQL/Redis/pgvector)
├── .env.example
├── sql/
│   ├── schema.sql                    # all 18 MySQL tables
│   ├── init-data.sql                 # seed Prompt templates
│   └── pgvector-init.sql             # vector collections & HNSW indexes
├── socialflow-common/                # R, exceptions, enums, utils
├── socialflow-model/                 # Entity / DTO / VO
├── socialflow-dao/                   # MyBatis-Plus mappers
├── socialflow-service/               # business + AI core
│   └── src/main/java/com/socialflow/service/
│       ├── ai/{llm,prompt,rag,embedding,guardrails,agent,eval,memory,tools}
│       ├── content/
│       ├── knowledge/
│       ├── media/
│       ├── publish/
│       └── user/
├── socialflow-web/                   # controllers + global exception handler
├── socialflow-admin/                 # Spring Boot main class + application.yml
└── socialflow-ui/                    # Vue 3 + Vite frontend
```

## Quick Start

### 1. Prerequisites
- JDK 21+
- Maven 3.9+
- Node.js 20 LTS
- Docker / Docker Compose

### 2. Bring up middleware

```bash
cp .env.example .env
# fill in AI_ENCRYPTION_KEY (64 hex chars) and any other secrets
docker compose up -d
```

This starts MySQL (port 3306), Redis (6379), pgvector (5432),
and seeds MySQL from `sql/schema.sql` + `sql/init-data.sql`.

### 3. Initialize pgvector

```bash
docker exec -i socialflow-pgvector psql -U postgres -d socialflow_vec < sql/pgvector-init.sql
```

### 4. Build and run the backend

```bash
mvn -DskipTests clean package
java -jar socialflow-admin/target/socialflow-admin.jar
```

or from your IDE: run `com.socialflow.SocialFlowApplication`.

Open the API docs at <http://localhost:8080/doc.html>.

### 5. Run the frontend

```bash
cd socialflow-ui
npm install
npm run dev
```

Open <http://localhost:5173>.

## Environment Variables

Set in `.env` (picked up by Docker Compose) and in your shell (picked up by the JVM).
See `.env.example` for the complete list. Required:

| Variable | Purpose |
|---|---|
| `AI_ENCRYPTION_KEY` | AES-256-GCM key (32 bytes hex = 64 chars) used to encrypt user API keys |
| `DB_PASSWORD` | MySQL root password |
| `REDIS_PASSWORD` | Redis AUTH password |
| `COS_SECRET_ID` / `COS_SECRET_KEY` | 腾讯云 COS credentials |
| `PG_PASSWORD` | pgvector postgres password |
| `WECHAT_MP_SECRET` | WeChat Official Account AppSecret (if auto-publish is enabled) |

## Feature Map

| Capability | Module | Status |
|---|---|---|
| Prompt template render | `service.ai.prompt` | interface |
| RAG pipeline (HyDE + hybrid + rerank) | `service.ai.rag` | skeleton |
| Document chunking | `service.ai.rag.DocumentChunker` | interface |
| Vector store abstraction (pgvector/Milvus) | `service.ai.embedding.VectorStoreService` | interface |
| Guardrails (responsibility chain) | `service.ai.guardrails` | chain + 2 rules |
| LLM provider router (DeepSeek/Qwen/OpenAI/Claude) | `service.ai.llm` | router + stub |
| Multi-Agent orchestration | `service.ai.agent` | interface |
| LLM-as-Judge evaluation | `service.ai.eval` | interface |
| Memory (session + long-term) | `service.ai.memory` | interface |
| Skills/Tools registry | `service.ai.tools` | registry |
| Content CRUD + generation | `service.content` | partial |
| Knowledge base ingestion | `service.knowledge` | interface |
| Publish strategy (WeChat MP / assisted) | `service.publish` | interface |
| SSE streaming (token + stage) | `web.controller.ContentController` | implemented |
| Sa-Token auth + JWT | `web`                                  | implemented |
| Global exception handler | `web.handler.GlobalExceptionHandler` | implemented |

## API Endpoints (subset)

All endpoints are under `/api/v1`. Unified return body is `{code, message, data, timestamp}`.

```
POST  /auth/register
POST  /auth/login
GET   /auth/me

POST  /content/generate               Single platform generation
POST  /content/generate-stream        Streaming SSE
POST  /content/generate-batch         Multi-platform fan-out
POST  /content/generate-with-rag      RAG-enabled generation
POST  /content/generate-multi-agent   Multi-Agent pipeline (SSE)
POST  /content/rewrite
POST  /content/generate-title
POST  /content/suggest-hashtags
POST  /content/similar
GET   /content/list
GET   /content/{id}
PUT   /content/{id}
DELETE /content/{id}

POST  /kb                             Create knowledge base
GET   /kb/list
POST  /kb/{kbId}/upload               Upload a document (async parse)
POST  /kb/{kbId}/search               Retrieval playground
DELETE /kb/{kbId}/doc/{docId}

POST  /eval/task                      Create A/B eval task
POST  /eval/task/{taskId}/run         Run task asynchronously
GET   /eval/task/{taskId}/report
```

## Phase Plan

| Phase | Focus | Deliverables |
|---|---|---|
| 1 | MVP | Auth + single/multi platform generation + SSE + templates |
| 2 | RAG | Document upload/parse/chunk/embed + pipeline + similar search |
| 3 | Guardrails & Tools | Input/output guardrails, @Tool registration, Skills |
| 4 | Multi-Agent | Planner/Writer/Reviewer/Optimizer + stage streaming |
| 5 | Eval & Memory | LLM-as-Judge, A/B reports, session + long-term memory |
| 6 | Publish & polish | WeChat MP API, scheduling, dashboard, media semantic search |

## References

- Design doc: `docs/商品文案生成.docx` (project design v2.0)
- Tech spec: `docs/文案生成技术文档.docx` (tech doc v1.0)

## License

For personal learning and engineering practice. No warranty.
