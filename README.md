# Developer Tools Intelligence Platform — Working Slice

A working, runnable slice of the platform we designed: Jira ticket CSV → LLM
enrichment (OpenAI) → embeddings (OpenAI) → in-memory vector store → RAG chat,
with a React frontend.

```
devtools-platform/
├── backend/      Spring Boot 3.3 / Java 21 — ingestion + enrichment + embedding + RAG chat API
└── frontend/     React 19 + Vite — chat UI
```

## What this does, end to end

1. On startup, the backend reads `backend/src/main/resources/data/jira_tickets_sample.csv`
   (21 sample tickets across CI/CD, IDE Plugin, Code Review Tool, Artifact Registry, Secrets Manager)
2. For each **resolved** ticket, it calls OpenAI to classify it (tool, category, severity)
   and produce a clean factual summary — this is the "LLM enrichment" stage
3. It embeds the enriched summary + ticket title using OpenAI's embeddings API
4. It stores the result in an in-memory vector store (a `List` searched via cosine similarity —
   this stands in for pgvector from the full architecture; same role, no DB needed for this slice)
5. The React chat UI lets you ask questions; the backend embeds your question, retrieves the
   most similar resolved tickets, and asks the LLM to answer **using only those retrieved tickets**
   — this is Retrieval-Augmented Generation (RAG)

Every answer comes with source citations (ticket ID + similarity score) so you can see exactly
which past tickets the answer was grounded in.

## ⚠️ Important — this was built without the ability to compile/run Java

I wrote this Java/Spring Boot code carefully and cross-checked every constructor call and method
signature manually, but **the sandbox I built this in cannot reach Maven Central**, so I was not
able to actually run `mvn compile` to verify it builds. The React frontend, by contrast, I *did*
run, build, lint, and visually verify with screenshots — that part is confirmed working.

When you run the backend for the first time, you may hit a small compile error I missed (typo,
import, etc). If you do: paste me the exact error and I'll fix it immediately — these are
typically one-line fixes once a real compiler points at the problem.

---

## Setup

### 1. Backend

**Requirements:** Java 21, Maven 3.8+

```bash
cd backend
```

Open `src/main/resources/application.yml` and paste your real OpenAI API key:

```yaml
openai:
  api-key: sk-...your-real-key-here...
```

Then run:

```bash
mvn spring-boot:run
```

Watch the console — you'll see each ticket being enriched and embedded in real time:

```
Enriched CICD-1421 -> category=auth, severity=critical
Enriched CICD-1388 -> category=auth, severity=high
...
=== Ingestion pipeline complete in 14823 ms ===
Summary: 16 enriched, 16 embedded, 5 skipped, 16 total in vector store
```

(5 tickets are skipped because they're unresolved — `Open`/`In Progress` status. Only resolved
tickets go into the knowledge base, since unresolved tickets don't have an answer to retrieve yet.)

The backend runs on **http://localhost:8080**.

**Cost note:** this makes ~16 chat completion calls + ~16 embedding calls on startup using
`gpt-4o-mini` and `text-embedding-3-small` (both cheap models) — this should cost a small fraction
of a cent total. Each chat question you ask afterward makes one embedding call + one chat
completion call.

### 2. Frontend

**Requirements:** Node 18+

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** in your browser.

The header will show "Connecting..." until the backend finishes ingestion, then switch to
"N tickets indexed" once ready. You can use the "Filter by tool" dropdown to scope questions to
a specific tool (e.g. only search CI/CD Pipeline tickets).

---

## Try asking

- "Why does CI/CD fail silently on token expiry?"
- "What causes the IDE plugin to freeze?"
- "How do I fix 403 errors on the artifact registry?"
- "What happens when a branch name has a # in it?"
- "Tell me about secrets manager rotation issues"

Each answer will cite specific ticket IDs and show a similarity score for each source.

---

## How this maps to the full platform design

| This slice | Full platform (production) |
|---|---|
| CSV file | Jira REST API + ServiceFirst API + Teams Graph API |
| In-memory `List<VectorDocument>` | PostgreSQL + pgvector with HNSW index |
| Runs once on startup | Scheduled incremental ingestion (new/changed tickets only) |
| Single chat endpoint | Chat + analytics dashboard + trend alerting |
| One enrichment prompt | Same prompt, but with classification, dedup, and noise-filtering on raw comments |

The enrichment prompt, the RAG retrieval pattern, and the "only embed resolved tickets" rule
are all identical to what we discussed in the architecture — this slice just swaps the production
data sources and database for things you can run on a laptop with no setup.

## Known limitations of this slice (by design, not bugs)

- **No persistence** — restart the backend and it re-ingests from CSV every time (cheap with
  cache-friendly models, but not how production would work)
- **No incremental ingestion** — every restart re-processes every ticket, since there's no
  database to check "have I seen this ticket before"
- **No comment preprocessing pipeline** — the CSV comments are already clean for the demo;
  the full noise-filtering/signal-scoring pipeline we designed isn't built here, since it adds
  complexity that doesn't change the core RAG concept being demonstrated
- **Single fixed CSV** — to test with your own tickets, replace the CSV following the same column
  format: `ticket_id,summary,description,comments,status,priority,labels,created_date,resolved_date,tool_name`
