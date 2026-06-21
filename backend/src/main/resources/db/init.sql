-- Enable the pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Structured knowledge base ─────────────────────────────────────────────────
-- Stores the LLM-enriched ticket fields alongside the raw ticket metadata.
-- This is the SQL-queryable side of the platform: used for the ticket browser,
-- analytics queries (counts by tool/category/severity), and as the source of
-- truth for "what tickets do we have indexed?".
CREATE TABLE IF NOT EXISTS tickets (
    id                BIGSERIAL PRIMARY KEY,
    ticket_id         VARCHAR(50)   NOT NULL UNIQUE,
    tool_name         VARCHAR(100)  NOT NULL,
    category          VARCHAR(50)   NOT NULL,
    severity          VARCHAR(20)   NOT NULL,
    pain_point        TEXT          NOT NULL,
    summary           TEXT          NOT NULL,
    status            VARCHAR(30),
    priority          VARCHAR(20),
    created_date      DATE,
    resolved_date     DATE,
    embedded_text     TEXT,         -- the exact text that was embedded (for reference)
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ── Vector store ──────────────────────────────────────────────────────────────
-- Stores the embedding alongside a foreign key back to tickets.
-- Keeping vectors in a separate table means:
--   1. The tickets table stays narrow and fast for SQL analytics queries
--   2. You can re-embed without touching the structured data (e.g. model upgrade)
--   3. The HNSW index only lives on this table
CREATE TABLE IF NOT EXISTS ticket_embeddings (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   VARCHAR(50)  NOT NULL UNIQUE REFERENCES tickets(ticket_id) ON DELETE CASCADE,
    embedding   vector(1536) NOT NULL,          -- text-embedding-3-small = 1536 dims
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- HNSW index for fast approximate nearest-neighbour search.
-- cosine distance (<=> operator) matches what the embedding model is optimised for.
-- m=16, ef_construction=64 are sensible defaults for 10K-100K vectors at this dimensionality.
CREATE INDEX IF NOT EXISTS ticket_embeddings_hnsw_idx
    ON ticket_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ── Useful indexes for analytics queries ─────────────────────────────────────
CREATE INDEX IF NOT EXISTS tickets_tool_idx      ON tickets(tool_name);
CREATE INDEX IF NOT EXISTS tickets_category_idx  ON tickets(category);
CREATE INDEX IF NOT EXISTS tickets_severity_idx  ON tickets(severity);
CREATE INDEX IF NOT EXISTS tickets_created_idx   ON tickets(created_date);
