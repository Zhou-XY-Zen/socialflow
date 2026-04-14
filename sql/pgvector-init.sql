-- =====================================================================
-- SocialFlow - pgvector initialization
-- Run inside PostgreSQL:  psql -U postgres -d socialflow_vec -f pgvector-init.sql
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ----------------------------------------------------------------------
-- kb_chunks: knowledge base chunk vectors
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_chunks (
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT       NOT NULL,
    doc_id       BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL,
    source       VARCHAR(256),
    embedding    VECTOR(1024) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kb_chunks_kb     ON kb_chunks (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunks_hnsw   ON kb_chunks USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 256);

-- ----------------------------------------------------------------------
-- content_vectors: historical content (for similar content recommendation)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS content_vectors (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    content_id  BIGINT       NOT NULL,
    platform    VARCHAR(32)  NOT NULL,
    tags        VARCHAR(512),
    embedding   VECTOR(1024) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_content_vectors_user ON content_vectors (user_id);
CREATE INDEX IF NOT EXISTS idx_content_vectors_hnsw ON content_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 256);

-- ----------------------------------------------------------------------
-- media_vectors: media asset description vectors
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS media_vectors (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    media_id    BIGINT       NOT NULL,
    file_type   VARCHAR(32)  NOT NULL,
    tags        VARCHAR(512),
    embedding   VECTOR(1024) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_media_vectors_user ON media_vectors (user_id);
CREATE INDEX IF NOT EXISTS idx_media_vectors_hnsw ON media_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 256);

-- ----------------------------------------------------------------------
-- memory_vectors: long-term user memory
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS memory_vectors (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    memory_type  VARCHAR(32)  NOT NULL,
    content_text TEXT         NOT NULL,
    embedding    VECTOR(1024) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_memory_vectors_user ON memory_vectors (user_id);
CREATE INDEX IF NOT EXISTS idx_memory_vectors_hnsw ON memory_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 256);
