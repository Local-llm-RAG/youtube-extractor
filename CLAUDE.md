# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

It intentionally stays small. Deeper reference material lives in [`docs/`](./docs/README.md) and in the agent definitions under [`.claude/agents/`](./.claude/agents/).

## MANDATORY: Route Through Lead Architect (READ THIS FIRST)

**STOP. Before doing ANYTHING beyond pure read-only exploration, you MUST delegate to the Lead Architect agent.**

```
Agent(subagent_type="Lead Architect", prompt="<full user request with context>")
```

**This applies to:**
- ANY task that involves, or will lead to, code/config/infrastructure changes
- Analysis or investigation that the user intends to act on (e.g. "analyze X and fix it", "find the problem and propose a solution")
- Bug fixes, features, refactors, migrations, Docker/config changes
- Even if the user says "analyze first" — if the end goal is implementation, route immediately

**The ONLY exceptions (all three conditions must be true: read-only, no intent to change, user is just asking):**
- Pure information questions ("what does X do?", "where is Y defined?", "explain this code")
- Codebase exploration with NO implementation intent
- Git operations (commit, push, PR) explicitly requested by user

**When in doubt, route to Lead Architect.** It is always safer to delegate than to act directly. The Lead Architect will read files, plan, decompose, and spawn the correct specialist agents.

**DO NOT:**
- Read files and start analyzing yourself before delegating (the Lead Architect does this)
- Propose solutions yourself and then delegate implementation (the Lead Architect owns the plan)
- Invoke implementer agents directly (only the Lead Architect spawns them)

---

## Project Overview

A Spring Boot 4.0.1 data extraction and processing platform that aggregates research papers (ArXiv, Zenodo, PubMed Central, PMC S3) and YouTube videos, enriches them with AI-powered embeddings on demand, and serves the curated dataset to AI startups, institutions, and pharma companies. Data completeness and accuracy are the product.

The project collects data from four sources:

1. **YouTube** — Video metadata via YouTube API, transcripts via a Python service, stored in PostgreSQL. Supports GPT-powered transcript transformation with cost estimation.
2. **Research Papers (OAI-PMH)** — ArXiv, Zenodo, and PubMed Central harvested via OAI-PMH. PDFs are downloaded, processed through GROBID, language-detected, and persisted.
3. **PMC S3 Direct** — PMC Open Access dataset pulled directly from the `pmc-oa-opendata` S3 bucket over plain HTTPS (no AWS SDK). JATS XML is parsed natively (no GROBID) and filtered by commercial license.
4. **Query / RAG** — Vector / database lookup, LLM cost estimation, and GPT transformations.

For architectural patterns, pipeline diagrams, integrations, REST endpoints, package structure, and database column details, see [`docs/`](./docs/README.md).

## Build & Run Commands

```bash
./gradlew build            # Build
./gradlew bootRun          # Run (requires PostgreSQL, env vars, Docker services)
./gradlew test             # Run all tests
./gradlew test --tests "com.example.demo.YoutubeExtractorApplicationTests"  # Single test
```

**Required environment variables:** `YOUTUBE_API_KEY`, `QDRANT_API_KEY`, `GPT_API_KEY`

**Local services (Docker):**
```bash
cd docker && docker compose up -d   # Starts Qdrant, GROBID, Traefik
```
Also requires: PostgreSQL on `localhost:5432`, Python FastAPI RAG service on `localhost:8000`.

## Base package and tech stack

**Base package:** `com.data` — package-per-feature layout. Full tree in [`docs/package-structure.md`](./docs/package-structure.md).

**Tech stack:** Java 25, Spring Boot 4.0.1, Gradle, Lombok, Flyway, JPA / Hibernate, WebFlux, Spring Batch, Resilience4j, Jsoup, Apache Tika, jtokkit (token counting), JAXB (PMC OA response mapping).

## Database: Migration Rules (MANDATORY)

Schema is managed by Flyway in `src/main/resources/db/migration/`. Hibernate runs in `validate` mode — any schema change requires a new migration file. The `DataSource` enum is stored as `VARCHAR`, so new Java enum values need no migration.

For column-level details and the current schema conventions, see [`docs/database.md`](./docs/database.md). The rules below are enforcement — they stay in this file.

**All Flyway migrations must be fully idempotent — re-running a migration against a database where it has already been applied (in whole or in part) must succeed without error and leave the schema unchanged.**

Concrete requirements:
- `CREATE TABLE` / `CREATE SEQUENCE` / `CREATE INDEX` / `CREATE VIEW` → always use `IF NOT EXISTS`.
- `ALTER TABLE ... ADD COLUMN` / `DROP COLUMN` → always use `IF NOT EXISTS` / `IF EXISTS`.
- `DROP TABLE` / `DROP INDEX` / `DROP SEQUENCE` → always use `IF EXISTS`.
- `ALTER TABLE ... RENAME COLUMN` / `RENAME CONSTRAINT` and other statements that have no native `IF [NOT] EXISTS` form → wrap in a `DO $$ ... END $$` block that checks `information_schema.columns` / `pg_constraint` / `pg_indexes` first.
- Adding constraints (`ADD CONSTRAINT`, `ADD FOREIGN KEY`, `ADD UNIQUE`) → guard with a `DO` block that queries `pg_constraint` by name, or prefer `CREATE UNIQUE INDEX IF NOT EXISTS` where semantically equivalent.
- Data backfills (`UPDATE` / `INSERT`) → must be safe to re-run (idempotent `WHERE` clauses, `INSERT ... ON CONFLICT DO NOTHING`, etc.).
- Every migration header must end with a short note confirming idempotency, e.g. `-- This migration is idempotent and safe to re-run.`

Why: Flyway's schema history table normally prevents re-execution, but development databases, repair operations, and manual partial applies routinely break that guarantee. Idempotent migrations eliminate an entire class of "works on CI, breaks locally" failures and make `flyway repair` far safer.

## Code Quality Standards (summary)

All code must follow these principles. The short form lives here because agents apply these on every task; fuller narrative belongs in the per-agent definitions where relevant.

- **Readability:** self-explanatory naming, small focused methods, named constants instead of magic numbers.
- **DRY and reuse:** extract repeated logic (`OaiHttpSupport`, `AbstractOaiService`). Prefer composition; the only inheritance is the one-level-deep `AbstractOaiService` template method.
- **SOLID:** Single responsibility per class; extend via new implementations not modification; handler implementations are interchangeable; keep interfaces focused (`OaiSourceHandler` is exactly 3 methods); depend on abstractions.
- **Functional style where appropriate:** pure functions for parsing / filtering / mapping, streams for collection transforms, records for DTOs, isolate I/O at boundaries.
- **Error handling and resilience:** per-record failures must not halt the batch; use Resilience4j `@Retry` + `@RateLimiter` for external calls; validate at system boundaries; log meaningful context. `LanguageDetector` access is synchronized; tracker updates use atomic DB operations.
- **Configuration:** operational tuning (timeouts, delays, model names, pricing) in `application.yml`; business-rule constants as named `static final` fields. OAI source configs follow `oai.{source}.base-url`, `metadata-prefix`, `pagination-delay-ms`.
- **Licensing (non-negotiable):** only process papers with commercially-usable licenses (CC0, CC-BY, CC-BY-SA, MIT, Apache-2.0, BSD). Reject -NC and -ND variants.

## Agent Roster

| Agent | Role | Owns |
|-------|------|------|
| **Lead Architect** | Orchestrates, plans, delegates. Never edits code. | Work decomposition and sequencing |
| **OAI Implementer** | Implements OAI-PMH pipeline code | `oai/arxiv/**`, `oai/zenodo/**`, `oai/pubmed/**`, `oai/pipeline/**`, `oai/shared/**` |
| **GROBID Implementer** | Implements PDF → TEI processing | `oai/grobid/**` |
| **PMC S3 Implementer** | Implements PMC S3 direct pipeline | `pmcs3/**` |
| **Infrastructure Implementer** | Implements persistence, schema, config | Entities, repos, migrations, `application.yml`, `config/**`, shared contracts |
| **Code Reviewer** | Reviews changes post-implementation | Read-only review of any file |
| **Refactoring Agent** | Improves code quality, no logic changes | Any file (behavior-preserving only) |
| **Tester** | Writes and runs tests | `src/test/**` |
| **Documentation Agent** | Creates and updates all markdown documentation. Read-only on code. Spawned by the Lead Architect when architecture / pipelines / integrations / agent roster change. | `CLAUDE.md`, `docs/**`, `.claude/agents/*.md` |
| **Manual Tester** | Starts app, monitors logs, reports runtime behavior. Only spawned on explicit user request. | `manual-test-reports/` (output only) |
| **OAI Quality Auditor** | Autonomously audits OAI data quality via read-only DB queries (MCP). Only spawned on explicit user request. | `oai-quality-reports/` (output only) |

Agents must respect ownership boundaries. Cross-boundary changes require coordination through the Lead Architect. See [`.claude/agents/`](./.claude/agents/) for the detailed agent definitions, and the Lead Architect agent file for the exact criteria that decide when the Documentation Agent is spawned.

Agents must not execute git commands like commit, push, etc. This is handled by the user.
