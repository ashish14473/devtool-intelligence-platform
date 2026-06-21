-- ── Phase 1: Enrichment-time analytics columns ───────────────────────────────
-- These are populated during the existing enrichment pipeline (single LLM call)

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS sentiment_score      INTEGER,      -- 1(positive) to 5(frustrated)
    ADD COLUMN IF NOT EXISTS frustration_flag     BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS recurrence_signal    BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS resolution_type      VARCHAR(20),  -- FIXED|WORKAROUND|UNANSWERED|ABANDONED
    ADD COLUMN IF NOT EXISTS root_cause_tool      VARCHAR(100), -- actual tool causing issue (may differ from tool_name)
    ADD COLUMN IF NOT EXISTS knowledge_gap_flag   BOOLEAN DEFAULT FALSE; -- true if this looks like a doc gap

-- ── Phase 2: Scheduled job analytics tables ───────────────────────────────────

-- Pain pattern clusters — populated by weekly DBSCAN clustering job
CREATE TABLE IF NOT EXISTS pain_clusters (
    id              BIGSERIAL PRIMARY KEY,
    cluster_label   TEXT NOT NULL,              -- LLM-generated cluster name
    root_cause      TEXT NOT NULL,              -- LLM-generated root cause description
    ticket_ids      TEXT NOT NULL,              -- comma-separated ticket IDs in this cluster
    ticket_count    INTEGER NOT NULL,
    tools_affected  TEXT,                       -- comma-separated tool names in cluster
    first_seen      DATE,
    last_seen       DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Documentation gaps — populated by weekly knowledge gap analysis job
CREATE TABLE IF NOT EXISTS documentation_gaps (
    id                      BIGSERIAL PRIMARY KEY,
    suggested_title         TEXT NOT NULL,      -- LLM-suggested article title
    suggested_outline       TEXT NOT NULL,      -- LLM-suggested article outline
    triggering_ticket_ids   TEXT NOT NULL,      -- comma-separated tickets that triggered this gap
    tickets_prevented       INTEGER NOT NULL,   -- estimated tickets this article would prevent
    category                VARCHAR(50),
    tool_name               VARCHAR(100),
    priority_score          INTEGER,            -- tickets_prevented * avg_resolution_minutes
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Emerging issues — populated by daily emerging issue detection job
CREATE TABLE IF NOT EXISTS emerging_issues (
    id              BIGSERIAL PRIMARY KEY,
    title           TEXT NOT NULL,              -- LLM-generated issue title
    description     TEXT NOT NULL,              -- LLM hypothesis about the new pattern
    ticket_ids      TEXT NOT NULL,              -- triggering ticket IDs
    ticket_count    INTEGER NOT NULL,
    tool_name       VARCHAR(100),
    detected_date   DATE NOT NULL,
    confidence      VARCHAR(10),                -- HIGH|MEDIUM|LOW based on cluster tightness
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS tickets_resolution_type_idx   ON tickets(resolution_type);
CREATE INDEX IF NOT EXISTS tickets_frustration_idx       ON tickets(frustration_flag);
CREATE INDEX IF NOT EXISTS tickets_root_cause_tool_idx   ON tickets(root_cause_tool);
CREATE INDEX IF NOT EXISTS emerging_issues_date_idx      ON emerging_issues(detected_date);
