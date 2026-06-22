# Developer Tools Intelligence Platform — Claude Code Context

## What this project is

An AI-powered developer tools analytics platform built as a proof of concept. The core idea: developers raise tickets in Jira when they hit issues with internal tools (SonarQube, GitLab, Jenkins, Confluence, Nexus). We ingest those tickets, enrich them with LLM analysis, embed them into a vector database, and surface two things:

1. **A searchable knowledge base** — developers ask plain-language questions and get answers grounded in real past resolutions via RAG (Retrieval-Augmented Generation)
2. **An analytics dashboard** — leadership gets LLM-derived insights that Jira cannot provide natively: frustration signals, resolution quality, cross-tool dependencies, knowledge gaps, recurring patterns

There is also a **live Jira agent** that listens for new ticket creation via webhook, searches the knowledge base, and automatically posts an AI-suggested answer as a comment on the ticket.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend framework | Spring Boot 3.3.4, Java 21 |
| ORM | Spring Data JPA + Hibernate |
| JDBC | Spring JDBC (JdbcTemplate) for pgvector operations |
| Vector database | PostgreSQL 16 + pgvector extension |
| LLM / Embeddings | OpenAI API — gpt-4o-mini + text-embedding-3-small (1536 dims) |
| HTTP client | Spring RestClient (two beans: openAiRestClient, jiraRestClient) |
| CSV parsing | OpenCSV 5.9 |
| Frontend | React 19, Vite, Recharts |
| Infrastructure | Docker Compose (pgvector/pgvector:pg16 image) |
| Build | Maven 3.8+ |
| Java version | Java 21 (uses virtual threads via Thread.ofVirtual()) |

---

## Project structure

```
devtools-platform/
├── docker-compose.yml                          # PostgreSQL + pgvector container
├── README.md
├── CLAUDE.md                                   # this file
│
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/devtools/intelligence/
│       │   ├── IntelligencePlatformApplication.java   # Spring Boot entry point
│       │   │
│       │   ├── config/
│       │   │   ├── OpenAiProperties.java              # binds openai.* from application.yml
│       │   │   ├── JiraProperties.java                # binds jira.* from application.yml
│       │   │   ├── RestClientConfig.java              # produces openAiRestClient + jiraRestClient beans
│       │   │   └── CorsConfig.java                    # allows React dev server (localhost:5173)
│       │   │
│       │   ├── model/
│       │   │   ├── JiraTicket.java                    # raw ticket (from JSON or CSV)
│       │   │   ├── JiraTicketJson.java                # JSON file structure (issue + comments)
│       │   │   │   └── inner classes: IssueWrapper, Fields, Author, CommentsWrapper, Comment, AdfNode
│       │   │   ├── EnrichedTicket.java                # LLM enrichment output (12 fields)
│       │   │   ├── TicketEntity.java                  # JPA entity for tickets table (17 cols)
│       │   │   ├── VectorDocument.java                # in-memory vector doc (legacy, keep for reference)
│       │   │   ├── PainClusterEntity.java             # JPA entity for pain_clusters table
│       │   │   ├── DocumentationGapEntity.java        # JPA entity for documentation_gaps table
│       │   │   └── EmergingIssueEntity.java           # JPA entity for emerging_issues table
│       │   │
│       │   ├── repository/
│       │   │   ├── TicketRepository.java              # JPA repo with all analytics queries + projections
│       │   │   ├── PainClusterRepository.java
│       │   │   ├── DocumentationGapRepository.java
│       │   │   └── EmergingIssueRepository.java
│       │   │
│       │   ├── service/
│       │   │   ├── JsonIngestionService.java          # reads .json files → List<JiraTicket>
│       │   │   ├── CsvIngestionService.java           # legacy CSV reader (kept for reference)
│       │   │   ├── AdfExtractor.java                  # recursively extracts plain text from ADF nodes
│       │   │   ├── CommentPreprocessor.java           # filters bot comments, extracts ADF text
│       │   │   ├── EnrichmentService.java             # single LLM call → EnrichedTicket (12 fields)
│       │   │   ├── EmbeddingService.java              # calls OpenAI embeddings API → float[]
│       │   │   ├── PgVectorStore.java                 # JDBC-based pgvector save + cosine search
│       │   │   ├── IngestionOrchestrator.java         # ApplicationReadyEvent → full pipeline
│       │   │   ├── ChatService.java                   # RAG flow: embed → search → generate
│       │   │   ├── PainClusteringService.java         # @Scheduled Sunday 2am DBSCAN clustering
│       │   │   ├── KnowledgeGapService.java           # @Scheduled Sunday 3am gap detection
│       │   │   ├── EmergingIssueService.java          # @Scheduled daily 6am new pattern detection
│       │   │   ├── TicketAgentService.java            # RAG agent for auto-answering new Jira tickets
│       │   │   └── JiraCommentService.java            # posts ADF comments to Jira via REST API
│       │   │
│       │   ├── controller/
│       │   │   ├── ChatController.java                # POST /api/chat
│       │   │   ├── AnalyticsController.java           # GET /api/analytics/* (8 endpoints)
│       │   │   ├── TicketController.java              # GET /api/tickets, /api/tickets/{id}
│       │   │   ├── StatusController.java              # GET /api/status, /api/tools
│       │   │   └── AgentController.java               # POST /api/agent/webhook, /api/agent/trigger
│       │   │
│       │   └── dto/
│       │       ├── ChatRequest.java                   # { question, toolFilter }
│       │       ├── ChatResponse.java                  # { answer, sources[] }
│       │       ├── SourceTicket.java                  # { ticketId, toolName, category, painPoint, similarityScore }
│       │       ├── TicketDto.java                     # read-only ticket view (17 fields + analytics)
│       │       ├── StatusResponse.java                # { ready, documentCount, message }
│       │       └── AgentWebhookPayload.java           # { issueKey, summary, description, issueType, priority, projectKey }
│       │
│       └── resources/
│           ├── application.yml                        # all config — DB, OpenAI, Jira, ingestion paths
│           ├── db/
│           │   └── init.sql                           # creates all tables + HNSW index (runs once on Docker start)
│           └── data/
│               ├── jira_tickets_sample.csv            # legacy CSV (kept for reference, not used)
│               └── sonarqube/                         # 55 JSON ticket files (active data source)
│                   ├── SQ-1001.json .. SQ-1016.json   # SonarQube (16 tickets)
│                   ├── GL-2001.json .. GL-2009.json   # GitLab (9 tickets, some In Progress)
│                   ├── JK-3001.json .. JK-3010.json   # Jenkins (10 tickets)
│                   ├── CF-4001.json .. CF-4010.json   # Confluence (10 tickets)
│                   └── NX-5001.json .. NX-5010.json   # Nexus (10 tickets)
│
└── frontend/
    ├── package.json                                   # React 19, Vite, Recharts
    ├── vite.config.js                                 # allowedHosts: 'all' (for tunnel access)
    └── src/
        ├── main.jsx
        ├── App.jsx                                    # two-tab shell: Chat + Knowledge Base
        ├── App.css                                    # full CSS including analytics styles
        └── components/
            ├── ChatTab.jsx                            # RAG chat UI with source cards
            └── KnowledgeTab.jsx                       # analytics + ticket browser
```

---

## Database schema

```sql
-- tickets: enriched structured store (SQL analytics side)
tickets (
  id, ticket_id, tool_name, category, severity, pain_point, summary,
  status, priority, created_date, resolved_date, embedded_text,

  -- Phase 1: enrichment-time analytics (single LLM call per ticket)
  sentiment_score,      -- INTEGER: 1(positive) to 5(frustrated)
  frustration_flag,     -- BOOLEAN DEFAULT FALSE
  recurrence_signal,    -- BOOLEAN DEFAULT FALSE
  resolution_type,      -- VARCHAR(20): FIXED|WORKAROUND|UNANSWERED|ABANDONED
  root_cause_tool,      -- VARCHAR(100): actual tool causing issue (may differ from tool_name)
  knowledge_gap_flag,   -- BOOLEAN DEFAULT FALSE: true if this looks like a doc gap

  created_at
)

-- ticket_embeddings: pgvector side (HNSW index on embedding column)
ticket_embeddings (
  id, ticket_id FK → tickets, embedding vector(1536), created_at
)

-- pain_clusters: populated by PainClusteringService (Sunday 2am)
pain_clusters (
  id,
  cluster_label,        -- TEXT: LLM-generated cluster name  ← column is cluster_label NOT cluster_name
  root_cause,           -- TEXT: LLM-generated root cause description
  ticket_ids,           -- TEXT: comma-separated ticket IDs
  ticket_count,         -- INTEGER
  tools_affected,       -- TEXT: comma-separated tool names
  first_seen,           -- DATE
  last_seen,            -- DATE
  created_at
)

-- documentation_gaps: populated by KnowledgeGapService (Sunday 3am)
documentation_gaps (
  id,
  suggested_title,      -- TEXT NOT NULL: LLM-suggested article title
  suggested_outline,    -- TEXT NOT NULL: LLM-suggested article outline
  triggering_ticket_ids,-- TEXT NOT NULL: comma-separated tickets that triggered this gap
  tickets_prevented,    -- INTEGER NOT NULL: estimated tickets this article would prevent
  category,             -- VARCHAR(50)
  tool_name,            -- VARCHAR(100)
  priority_score,       -- INTEGER: tickets_prevented * avg_resolution_minutes
  created_at
)

-- emerging_issues: populated by EmergingIssueService (daily 6am)
emerging_issues (
  id,
  title,                -- TEXT NOT NULL: LLM-generated issue title
  description,          -- TEXT NOT NULL: LLM hypothesis about the new pattern
  ticket_ids,           -- TEXT NOT NULL: triggering ticket IDs
  ticket_count,         -- INTEGER NOT NULL
  tool_name,            -- VARCHAR(100)
  detected_date,        -- DATE NOT NULL
  confidence,           -- VARCHAR(10): HIGH|MEDIUM|LOW based on cluster tightness
  created_at
)
```

**Critical column names to get right:**
- `pain_clusters.cluster_label` — NOT `cluster_name`. Java field is `clusterLabel`, `@Column(name = "cluster_label")`
- `emerging_issues.title` + `emerging_issues.detected_date` — NOT `description`/`detected_at` (different from earlier versions)
- `documentation_gaps.suggested_outline` is NOT NULL — must be populated by KnowledgeGapService
- `tickets` has NO `knowledge_gap_description` column — that field was removed from the schema



---

## application.yml — key config

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/devtools_intelligence
    username: devtools
    password: devtools_secret

openai:
  api-key: PASTE_YOUR_OPENAI_API_KEY_HERE   # gpt-4o-mini + text-embedding-3-small
  chat-model: gpt-4o-mini
  embedding-model: text-embedding-3-small
  embedding-dimensions: 1536

jira:
  base-url: https://aitian888.atlassian.net
  email: aitian.888@gmail.com
  api-token: PASTE_YOUR_JIRA_API_TOKEN_HERE
  project-key: SCRUM
  similarity-threshold: 0.75

ingestion:
  json-data-path: classpath:data/sonarqube   # reads all .json files in this directory
```

---

## How the ingestion pipeline works

On startup (`ApplicationReadyEvent`), `IngestionOrchestrator` runs this sequence for every resolved ticket in the JSON files:

1. `JsonIngestionService.loadTickets()` — reads all `.json` files, parses ADF comment bodies via `AdfExtractor`, filters bot comments via `CommentPreprocessor`, infers tool name from ticket key prefix (SQ→SonarQube, GL→GitLab, JK→Jenkins, CF→Confluence, NX→Nexus)

2. `EnrichmentService.enrich(ticket)` — single OpenAI chat completion call with JSON mode. Returns all 12 fields in one response: toolName, category, painPoint, summary, severity, resolutionType, sentimentScore, frustrationFlag, recurrenceSignal, rootCauseTool, knowledgeGapFlag, knowledgeGapDescription. Note: `knowledgeGapDescription` is used for the knowledge gap detection job but is NOT stored as a column on the `tickets` table — it is used transiently during ingestion.

3. `TicketRepository.save(entity)` — persists to PostgreSQL tickets table

4. `EmbeddingService.embed(text)` — embeds `enriched.summary + "\n" + ticket.summary`

5. `PgVectorStore.save(ticketId, vector)` — JDBC INSERT into ticket_embeddings with pgvector type

**Idempotency:** `ticketRepository.existsByTicketId()` check prevents re-processing on restart.

**Unresolved tickets are skipped** — only `status = Done/Closed/Resolved` tickets enter the knowledge base. Currently 7 of 55 tickets are In Progress/Open and are skipped. 48 tickets are indexed.

---

## How the RAG chat works

1. User posts `{ question, toolFilter }` to `POST /api/chat`
2. `ChatService` embeds the question using same model as ingestion
3. `PgVectorStore.search(queryVector, 5, toolFilter)` — SQL with `ORDER BY embedding <=> ? LIMIT 5` (HNSW cosine search), optional `WHERE tool_name = ?` filter
4. If best score < threshold → return "no relevant tickets found"
5. Build context block from top-5 retrieved tickets
6. Call OpenAI with grounded system prompt: "answer using ONLY these sources, cite ticket IDs"
7. Return `{ answer, sources[] }` — sources include ticketId, similarity score, painPoint

---

## How the Jira agent works

1. Developer creates a ticket in project SCRUM on `https://aitian888.atlassian.net`
2. Jira fires a webhook POST to `https://<tunnel>/api/agent/webhook`
3. `AgentController` parses the payload, verifies it's a `jira:issue_created` event for project SCRUM
4. Kicks off `TicketAgentService.handleNewTicket()` on a virtual thread (async — returns 200 to Jira immediately)
5. Same RAG pipeline as ChatService — embed ticket, search pgvector, generate answer
6. If best similarity score ≥ 0.75 → `JiraCommentService.postSuggestedAnswer()` posts comment with ADF body
7. Comment is clearly labelled "🤖 AI Suggested Answer" with source ticket citations and a disclaimer

**Tunnel setup:** VS Code port forwarding on port 8080, set to Public visibility. The public URL goes into Jira webhook config under Jira Settings → System → Webhooks.

**Manual test endpoint:** `POST /api/agent/trigger` with `{ issueKey, summary, description }` — fires the agent without needing a real webhook.

---

## JSON ticket file format

Each file in `data/sonarqube/` contains two Jira API responses:

```json
{
  "issue": {
    "key": "SQ-1001",
    "fields": {
      "summary": "...",
      "description": "plain text string",
      "status": "Done",
      "priority": "High",
      "assignee": { "displayName": "...", "emailAddress": "..." },
      "reporter": { "displayName": "...", "emailAddress": "..." },
      "labels": ["sonarqube", "scanner"],
      "created": "2025-10-05T09:15:00.000+0000",
      "resolutiondate": "2025-10-07T14:30:00.000+0000"
    }
  },
  "comments": {
    "comments": [
      {
        "id": "20001",
        "author": { "displayName": "...", "emailAddress": "..." },
        "body": {
          "type": "doc",
          "version": 1,
          "content": [
            { "type": "paragraph", "content": [{ "type": "text", "text": "..." }] }
          ]
        },
        "created": "2025-10-05T09:45:00.000+0000",
        "updated": "2025-10-05T09:45:00.000+0000"
      }
    ],
    "total": 4
  }
}
```

**Description is a plain string** (not nested ADF). Comments use minimal ADF: `doc → paragraph → text`.

---

## The 55 tickets — tools and categories

| Tool | Prefix | Tickets | Categories |
|---|---|---|---|
| SonarQube | SQ | 16 | scanner issue, code coverage, access, onboarding, performance, project deletion, enhancement |
| GitLab | GL | 9 | pipeline issue, migration support, GitLab Duo |
| Jenkins | JK | 10 | credential-issue, plugin-conflict, onboarding, performance, pipeline-issue |
| Confluence | CF | 10 | permissions, macro, search, onboarding, page-migration, performance, template |
| Nexus | NX | 10 | access, proxy-setup, cleanup-policy, onboarding, performance, repo-config |

**Intentional patterns baked in for analytics testing:**
- 4 credential-expiry incidents across different tools (for cross-tool dependency mapping — JK-3001, JK-3006, NX-5001, SQ-1006 all same root cause: 90-day password policy)
- 3 teams asking the same Jenkins onboarding question independently (JK-3003, JK-3007, JK-3009 — for knowledge gap detection)
- Recurring complaint signals in final comments (JK-3010, CF-4010, NX-5001)
- 7 unresolved tickets (Open/In Progress) — correctly skipped by ingestion

---

## Frontend — two tabs

### Chat tab (`ChatTab.jsx`)
- Status polling: `GET /api/status` every 2.5s until `ready: true`, then `GET /api/tools` once
- Tool filter dropdown populated from `/api/tools`
- Send message → `POST /api/chat` → renders answer bubble + source cards
- Source cards show: ticketId (monospace), tool · category, pain point, similarity % match
- "Sources from pgvector" label distinguishes from general AI

### Knowledge Base tab (`KnowledgeTab.jsx`)
Two sub-views: **Analytics** and **Ticket Browser**

**Analytics** has 5 sub-panels:
1. **Overview** — KPI row (total, frustrated%, recurring%, knowledge gaps, cross-tool) + by-tool bar + by-category bar + severity pills
2. **Resolution Quality** — pie chart (FIXED/WORKAROUND/UNANSWERED/ABANDONED) + stacked bar per tool + leadership insight
3. **Sentiment & Frustration** — avg sentiment score bar per tool (colour-coded) + frustration rate bar + frustrated ticket list + recurring ticket list
4. **Cross-Tool Dependencies** — dependency flow table (filed-against → root-cause tool) + leadership insight
5. **Knowledge Gaps** — preventable ticket KPIs + ranked documentation article suggestions + leadership insight

**Ticket Browser**: filterable list by tool, click any ticket for a slide-in detail panel showing all analytics fields (resolution type, sentiment score, frustration flag, recurrence signal, root cause tool, knowledge gap description)

---

## Analytics endpoints

| Endpoint | Returns |
|---|---|
| `GET /api/analytics/by-tool` | ticket counts per tool |
| `GET /api/analytics/by-category` | ticket counts per category |
| `GET /api/analytics/by-severity` | ticket counts per severity |
| `GET /api/analytics/heatmap` | counts per (tool, category) pair |
| `GET /api/analytics/resolution-quality` | FIXED/WORKAROUND/UNANSWERED/ABANDONED breakdown |
| `GET /api/analytics/sentiment` | avg sentiment score + frustration rate per tool + frustrated/recurring ticket lists |
| `GET /api/analytics/cross-tool` | dependencies where root cause tool ≠ filed-against tool |
| `GET /api/analytics/knowledge-gaps` | gap article suggestions + gap ticket list + rates |
| `GET /api/analytics/summary` | single object with all KPI numbers for the overview cards |

---

## Scheduled analytics jobs

### PainClusteringService — Sunday 2am
- Pulls all embeddings from pgvector via JDBC (`SELECT ticket_id, embedding::float4[] FROM ticket_embeddings JOIN tickets`)
- Parses vector string — PostgreSQL returns `{0.024734,...}` with curly braces, regex strips `[\\[\\]{}\\s]`
- Runs DBSCAN (eps=0.25 cosine distance, minPts=2) — pure Java implementation
- For each cluster with ≥2 tickets: calls OpenAI to name the cluster and describe the shared root cause
- Saves to `pain_clusters` table

**Known bug fixed:** `PainClusterEntity` uses `@Column(name = "cluster_name")` — the DB column is `cluster_name` NOT `cluster_label`

### KnowledgeGapService — Sunday 3am
- Fetches tickets with `knowledge_gap_flag = true` from the tickets table
- Groups similar gap tickets (same tool + category)
- Calls OpenAI to suggest documentation articles
- Saves to `documentation_gaps` table

### EmergingIssueService — daily 6am
- Fetches tickets created in last 7 days
- Embeds each and searches pgvector for historical matches (30+ days old)
- Clusters new tickets with no historical precedent
- If ≥3 tickets cluster with no history → calls OpenAI for emerging issue description + hypothesis
- Saves to `emerging_issues` table

---

## Known issues and fixes applied

1. **`findByCreatedDateAfter` missing** — added to `TicketRepository` as `List<TicketEntity> findByCreatedDateAfter(LocalDate date)`

2. **`findByTicketId` missing** — added as `Optional<TicketEntity> findByTicketId(String ticketId)`

3. **`findByKnowledgeGapFlagTrue` missing** — added to `TicketRepository`

4. **pgvector returns `{...}` not `[...]`** — `parseVector()` in `PainClusteringService` regex changed from `[\\[\\]\\s]` to `[\\[\\]{}\\s]`. PostgreSQL returns the float4[] cast with curly braces, not square brackets.

5. **`cluster_label` column does not exist error** — at runtime JPA queried `pce1_0.cluster_label` but the DB column was `cluster_name`. Fixed by updating `@Column(name = "cluster_name")` to `@Column(name = "cluster_label")` in `PainClusterEntity` to match the actual DB schema.

6. **Two `RestClient` beans ambiguity** — all services injecting `RestClient` use `@Qualifier("openAiRestClient")`. `JiraCommentService` and `TicketAgentService` use `@Qualifier("jiraRestClient")` and `@Qualifier("openAiRestClient")` respectively.

7. **Vite `allowedHosts` error** — `vite.config.js` has `server: { allowedHosts: 'all', host: true }` for tunnel access

---

## How to run

### 1. Start the database
```bash
# First time (fresh start):
docker-compose up -d

# If you have old data and need to re-ingest with new schema:
docker-compose down -v
docker-compose up -d
```

### 2. Configure credentials
Edit `backend/src/main/resources/application.yml`:
- Set `openai.api-key`
- Set `jira.api-token` (generate at https://id.atlassian.com/manage-api-tokens)

### 3. Start the backend
```bash
cd backend
mvn spring-boot:run
```
Watch logs — ingestion runs automatically. Expect:
```
=== Ingestion complete in ~200000ms: 48 processed, 0 skipped (existing), 7 skipped (unresolved), 0 failed ===
```

### 4. Start the frontend
```bash
cd frontend
npm install
npm run dev
# Opens at http://localhost:5173
```

### 5. Set up the Jira webhook (for the agent)
- Forward port 8080 via VS Code port forwarding, set to Public
- Copy the public URL
- In Jira: Settings → System → Webhooks → Create
  - URL: `https://<your-tunnel>/api/agent/webhook`
  - Events: Issue → created
  - JQL filter: `project = SCRUM`

### 6. Test the agent manually
```bash
curl -X POST http://localhost:8080/api/agent/trigger \
  -H "Content-Type: application/json" \
  -d '{"issueKey":"SCRUM-1","summary":"SonarQube scanner failing","description":"Pipeline fails after upgrade"}'
```

---

## Design principles — important for future development

1. **Single LLM call per ticket** — all 12 enrichment fields come from one OpenAI call. Never add a second LLM call per ticket during ingestion.

2. **Only resolved tickets in the knowledge base** — `isResolved()` check in `IngestionOrchestrator`. Open/In Progress tickets are skipped intentionally.

3. **Idempotent ingestion** — `existsByTicketId()` check means restarting the backend never re-processes or charges for already-indexed tickets.

4. **pgvector via JDBC, not JPA** — the `vector` type is not natively supported by Hibernate. `PgVectorStore` uses `JdbcTemplate` with `PGvector.addVectorType(conn)` for all vector operations. The tickets table uses JPA normally.

5. **Two RestClient beans** — `openAiRestClient` (Bearer token) and `jiraRestClient` (Basic auth: base64(email:apiToken)). Always use `@Qualifier` when injecting.

6. **Tool name from key prefix** — `JsonIngestionService.inferToolName()` maps SQ→SonarQube, GL→GitLab, JK→Jenkins, CF→Confluence, NX→Nexus. Add new tools here when extending the dataset.

7. **ADF comment format** — Jira Cloud v3 API uses Atlassian Document Format for comment bodies. `AdfExtractor` handles the recursive node tree. `JiraCommentService` wraps outgoing comments in ADF when posting back to Jira.

8. **Agent runs async** — `AgentController` returns 200 to Jira immediately, then processes on a virtual thread. This is required because Jira webhooks time out in ~10 seconds and LLM calls take longer.

---

## What has been built — progress summary

### Completed
- [x] Full ingestion pipeline: JSON → enrichment → embedding → pgvector + PostgreSQL
- [x] 55 realistic sample tickets across 5 tools with intentional analytics patterns
- [x] RAG chat endpoint with similarity threshold gating
- [x] React frontend: Chat tab + Knowledge Base tab
- [x] 6 LLM analytics fields added to enrichment prompt (resolution quality, sentiment, frustration, recurrence, cross-tool dependency, knowledge gap)
- [x] 5 analytics sub-panels on the frontend dashboard
- [x] Live Jira agent: webhook → RAG → auto-comment on ticket
- [x] 3 scheduled analytics jobs: clustering (DBSCAN), knowledge gaps, emerging issues
- [x] Docker Compose setup with pgvector
- [x] VS Code port forwarding for Jira webhook tunnel
- [x] Confirmed working: 48 tickets ingested, agent posting to Jira SCRUM project

### Not yet built (discussed, planned)
- [ ] Real Jira REST API ingestion (currently using JSON files as mock)
- [ ] Microsoft Teams as a data source (Graph API)
- [ ] ServiceFirst incident integration
- [ ] Trend alerting / spike detection / weekly digest emails
- [ ] Pain cluster and knowledge gap results surfaced in the frontend dashboard
- [ ] Incremental ingestion (only new/changed tickets, not full re-run)
- [ ] pgvector → production swap (currently using same pgvector for PoC and production path)
- [ ] Authentication / user management
- [ ] Multi-tenant / multi-project support beyond SCRUM

---

## Jira workspace details

- Instance: `https://aitian888.atlassian.net`
- Email: `aitian.888@gmail.com`
- Project key: `SCRUM`
- Webhook: configured for `jira:issue_created` events on project SCRUM
- API token: stored in `application.yml` (do not commit)

---

## Package structure for new code

All new Java code goes under `com.devtools.intelligence`:
- New services → `service/`
- New JPA entities → `model/`
- New repositories → `repository/`
- New REST controllers → `controller/`
- New request/response objects → `dto/`
- New config `@ConfigurationProperties` → `config/`

All new React components go in `frontend/src/components/`.

---

## Common commands

```bash
# Rebuild backend after changes
cd backend && mvn spring-boot:run

# Reset DB and re-ingest all tickets
docker-compose down -v && docker-compose up -d
# then restart backend

# Apply schema changes without data loss (run in DBeaver)
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS new_column VARCHAR(50);

# Check what is in pgvector
SELECT ticket_id, tool_name, category FROM tickets ORDER BY created_date DESC;
SELECT COUNT(*) FROM ticket_embeddings;

# Test RAG chat directly
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Why does SonarQube fail after upgrade?","toolFilter":"SonarQube"}'

# Check analytics
curl http://localhost:8080/api/analytics/summary
curl http://localhost:8080/api/analytics/sentiment
curl http://localhost:8080/api/analytics/knowledge-gaps

# Frontend dev server
cd frontend && npm run dev
```
