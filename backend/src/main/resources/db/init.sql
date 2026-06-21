-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Structured knowledge base ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tickets (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           VARCHAR(50)   NOT NULL UNIQUE,
    tool_name           VARCHAR(100)  NOT NULL,
    category            VARCHAR(50)   NOT NULL,
    severity            VARCHAR(20)   NOT NULL,
    pain_point          TEXT          NOT NULL,
    summary             TEXT          NOT NULL,
    status              VARCHAR(30),
    priority            VARCHAR(20),
    created_date        DATE,
    resolved_date       DATE,
    embedded_text       TEXT,

    -- ── Phase 2 analytics fields (enriched by LLM) ──────────────────────────
    -- Resolution Quality: FIXED | WORKAROUND | UNANSWERED | ABANDONED
    resolution_type     VARCHAR(20),

    -- Sentiment: 1 (very positive) to 5 (very frustrated)
    sentiment_score     INTEGER,

    -- True if developer comment thread shows frustration or dissatisfaction
    frustration_flag    BOOLEAN DEFAULT FALSE,

    -- True if comments suggest the problem is likely to recur
    recurrence_signal   BOOLEAN DEFAULT FALSE,

    -- The tool where the root cause actually lies (may differ from tool_name)
    root_cause_tool     VARCHAR(100),

    -- True if this ticket represents a documentation/knowledge gap
    knowledge_gap_flag  BOOLEAN DEFAULT FALSE,

    -- LLM-generated one-liner describing the knowledge gap if flagged
    knowledge_gap_description TEXT,

    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ── Vector store ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ticket_embeddings (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   VARCHAR(50)  NOT NULL UNIQUE REFERENCES tickets(ticket_id) ON DELETE CASCADE,
    embedding   vector(1536) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ticket_embeddings_hnsw_idx
    ON ticket_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ── Pain clusters (populated by weekly clustering job) ────────────────────────
CREATE TABLE IF NOT EXISTS pain_clusters (
    id              BIGSERIAL PRIMARY KEY,
    cluster_name    TEXT NOT NULL,
    root_cause      TEXT NOT NULL,
    tools_affected  TEXT,        -- comma-separated tool names
    ticket_ids      TEXT NOT NULL, -- comma-separated ticket IDs in this cluster
    ticket_count    INTEGER NOT NULL,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Documentation gaps (populated by knowledge gap job) ───────────────────────
CREATE TABLE IF NOT EXISTS documentation_gaps (
    id                      BIGSERIAL PRIMARY KEY,
    suggested_title         TEXT NOT NULL,
    suggested_outline       TEXT,
    triggering_ticket_ids   TEXT NOT NULL,
    tickets_prevented       INTEGER NOT NULL,
    tool_name               VARCHAR(100),
    category                VARCHAR(50),
    detected_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Emerging issues (populated by daily emerging issue job) ───────────────────
CREATE TABLE IF NOT EXISTS emerging_issues (
    id              BIGSERIAL PRIMARY KEY,
    description     TEXT NOT NULL,
    hypothesis      TEXT,        -- LLM hypothesis about the trigger
    ticket_ids      TEXT NOT NULL,
    ticket_count    INTEGER NOT NULL,
    tool_name       VARCHAR(100),
    confidence      DOUBLE PRECISION,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Analytics indexes ──────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS tickets_tool_idx            ON tickets(tool_name);
CREATE INDEX IF NOT EXISTS tickets_category_idx        ON tickets(category);
CREATE INDEX IF NOT EXISTS tickets_severity_idx        ON tickets(severity);
CREATE INDEX IF NOT EXISTS tickets_created_idx         ON tickets(created_date);
CREATE INDEX IF NOT EXISTS tickets_resolution_type_idx ON tickets(resolution_type);
CREATE INDEX IF NOT EXISTS tickets_frustration_idx     ON tickets(frustration_flag);
CREATE INDEX IF NOT EXISTS tickets_root_cause_tool_idx ON tickets(root_cause_tool);
CREATE INDEX IF NOT EXISTS tickets_knowledge_gap_idx   ON tickets(knowledge_gap_flag);
