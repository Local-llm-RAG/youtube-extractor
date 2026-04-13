# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

The project collects data from multiple sources:
1. **YouTube** — Fetches video metadata via YouTube API, downloads transcripts through a Python service (using youtube-transcript-api with webshare proxies), stores everything in PostgreSQL. Supports GPT-powered transcript transformation with cost estimation.
2. **Research Papers (OAI-PMH)** — Harvests metadata from ArXiv, Zenodo, and PubMed Central using the OAI-PMH protocol. Downloads PDFs, processes them through GROBID for structured extraction, detects language, and persists enriched data.
3. **PMC S3 Direct** — Harvests the PMC Open Access dataset directly from the `pmc-oa-opendata` S3 bucket over plain HTTPS (no AWS SDK). Uses daily inventory manifests for discovery, parses JATS XML natively (no GROBID), and filters by commercial license (CC0 / CC BY / CC BY-SA). Runs on its own schedule with tracker-based resume.
4. **Query/RAG** — Searches stored data via vector/database lookup, estimates LLM costs, and performs GPT transformations.

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
Also requires: PostgreSQL on localhost:5432, Python FastAPI RAG service on localhost:8000.

## Architecture

**Base package:** `com.data`

### Data Processing Pipelines

1. **OAI-PMH Path:** OAI protocol query → fetch metadata → download PDF → GROBID parse to TEI-XML → extract sections → language detect (Tika) → embed → store in database
2. **PMC S3 Path:** Daily inventory manifest → filter by license → download JSON metadata → download JATS XML + .txt → native JATS parse → language from `xml:lang` → store in database
3. **YouTube Path:** YouTube API fetch → download transcripts → chunk → embed → store in Qdrant
4. **Query Path:** Search → vector/database lookup → cost estimation → LLM transformation

### Core Architectural Patterns

- **Template Method + Strategy:** `AbstractOaiService` provides the shared OAI-PMH pagination loop and implements `OaiSourceHandler`. Each source (`ArxivOaiService`, `ZenodoOaiService`, `PubmedOaiService`) extends it with source-specific parsing and PDF resolution. Resolved via `OaiSourceRegistry` by `DataSource` enum. Adding a new source means one client class + one service extending `AbstractOaiService`.
- **Shared HTTP utilities:** `OaiHttpSupport` provides common OAI-PMH URI building, retryable error detection, and exchange handling. Each client delegates to `executeOaiExchange` with source-specific quirks via `IntPredicate`.
- **Facade:** `GenericFacade` coordinates the full OAI processing pipeline (fetch → filter → download → GROBID → language detect → persist). Per-record failures are isolated — one failure never halts the batch. Tracker progress is updated atomically at the DB level via `TrackerRepository.incrementProcessed`.
- **Event-driven:** `VideoDiscoveredEvent` / `VideoDiscoveredListener` for async YouTube processing.
- **Spring Batch:** `OAIProcessorService` runs batch jobs with configurable `daysBack` and `sources` list (`oai.processing.*`). Uses `PostgresAdvisoryLock` for distributed locking.
- **Resilience:** All OAI clients use Resilience4j `@Retry` + `@RateLimiter` annotations. Circuit breaker on transcript API (429 handling, `TranscriptRateLimitedException` only). Exponential backoff retry with configurable parameters in `application.yml`.

### Pipeline Flow (OAI Sources)

```
OAIProcessorService  →  GenericFacade
  └─ OaiSourceRegistry.get(source)
       └─ {Source}OaiService (extends AbstractOaiService)
            └─ fetchAllRecords() [template method: pagination loop]
                 └─ callListRecords() → {Source}Client → OAI-PMH API
                 └─ parseResponse() → source-specific XML parsing
  └─ Filter: skip already-processed IDs
  └─ Async via oaiExecutor:
       └─ {Source}OaiService.getPdf() → PDF bytes
       └─ GrobidService.processGrobidDocument() → PaperDocument
       └─ Language detection (Tika, synchronized)
       └─ PaperInternalService.persistState()
       └─ TrackerService.incrementProcessed() [atomic DB update]
```

### Pipeline Flow (PMC S3)

```
PmcS3ProcessorService (scheduled, advisory-locked)
  └─ Probe latest manifest.json (today → N days back)
  └─ PmcS3Facade.processBatch(manifestKey)
       └─ InventoryService.fetchInventory(manifestKey) → List<InventoryEntry>
       └─ Filter: skip already-persisted pmcIds (RecordRepository.findAllSourceIdsByDataSource)
       └─ Async via pmcS3Executor (virtual threads):
            └─ MetadataService.fetchMetadata(entry) → ArticleMetadata (JSON)
            └─ PmcS3LicenseFilter.isAcceptable(licenseCode)   [CC0 / CC BY / CC BY-SA only]
            └─ PmcS3Client.downloadText(jatsKey) → JATS XML
            └─ PmcS3Client.downloadText(txtKey) → rawContent
            └─ JatsParser.parse() → PaperDocument (native JATS, no GROBID)
            └─ JatsParser.extractLanguage() from xml:lang
            └─ PaperInternalService.persistState(PMC_S3, record, doc, pdfUrl)
            └─ PmcS3TrackerService.incrementProcessed() [atomic DB update]
  └─ Tracker marked COMPLETED / FAILED
```

### External Integrations

| Service | Purpose | Config |
|---------|---------|--------|
| GROBID (Docker, port 8070) | PDF → structured TEI-XML | `GrobidClient` with retry, `GrobidTeiMapperJsoup` for parsing, options configurable via `grobid.options.*` |
| Qdrant (Docker, ports 6333/6334) | Vector database for embeddings | `QdrantGrpcClient` via gRPC, with `@PreDestroy` cleanup |
| Python FastAPI (port 8000) | Embedding generation | `RagSystemRestApiService` / `RagSystemWebFluxClient` |
| OpenAI API | LLM transformations | `GPTClient` / `GptService` with configurable model and pricing (`gpt.model`, `gpt.pricing.*`) |
| Google YouTube Data API v3 | Video/channel metadata & transcripts | `YouTubeGateway` |
| ArXiv OAI | Research paper metadata | `ArxivClient` / `ArxivOaiService` |
| Zenodo OAI | Research paper metadata | `ZenodoClient` / `ZenodoOaiService` |
| PMC OAI | Biomedical paper metadata | `PubmedClient` / `PubmedOaiService` (OA links via JAXB) |
| PMC S3 (AWS Open Data) | Full-text biomedical articles (JATS, plain text, PDF) | `PmcS3Client` / `InventoryService` / `MetadataService` / `JatsParser`, plain HTTPS — no AWS SDK |

### REST API Endpoints

- `POST /api/arxiv/search` — Search ArXiv papers
- `GET /api/channels/mass` — Fetch videos from multiple channels
- `GET /api/channels/videos` — Fetch single channel videos
- `GET /api/channels/video` — Fetch single video by URL
- `POST /api/estimate/youtube`, `/youtube/channel`, `/arxiv` — GPT cost estimation
- `POST /api/embed` — Debug embedding endpoint (uses `EmbeddingProperties` defaults)
- Swagger UI at `/swagger`

### Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`. JPA with Hibernate in validate mode — schema changes require new migration files. `DataSource` (the shared enum in `com.data.shared`) is stored as `VARCHAR` (not a Postgres enum), so new Java enum values need no migration. Current values: `ARXIV`, `ZENODO`, `PUBMED`, `PMC_S3`.

Key column names to be aware of (all set by V24):
- `source_record.external_identifier` — the upstream provider id (OAI identifier for ArXiv/Zenodo/PubMed, PMID for PMC S3). Was previously `oai_identifier`.
- `record_document.source_xml` — the raw structured XML (TEI for OAI sources, JATS for PMC S3). Was previously `tei_xml`.
- `record_document.funding_list` — text[] of funding statements (currently populated only by the PMC S3 pipeline from `<funding-group>/<award-group>`).
- `record_author.orcid` — VARCHAR(64) holding the ORCID iD where supplied (PMC S3 reads this from JATS `<contrib-id>`).

#### Migration Rules (MANDATORY)

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

## Package Structure

Base package: `com.data`. Follows **package-per-feature** architecture.

### Core Feature Packages

```
youtube/                          # YouTube data ingestion
├── api/                          #   REST controllers
├── service/                      #   Business logic (channel fetching, transcript strategies)
├── event/                        #   Async event processing (VideoDiscoveredEvent/Listener)
└── persistence/
    ├── entity/                   #   JPA entities (ChannelEntity, Video, VideoTranscript, regions)
    └── repository/               #   Spring Data JPA repositories

oai/                              # OAI-PMH research paper harvesting
├── pipeline/                     #   Pipeline orchestration (GenericFacade, OAIProcessorService,
│                                 #     OaiSourceHandler, OaiSourceRegistry)
├── arxiv/                        #   ArXiv OAI client + service
│   └── search/                   #     ArXiv search REST API
├── pubmed/                       #   PubMed OAI client + service
│   └── oa/                       #     JAXB model for PMC OA Web Service
├── zenodo/                       #   Zenodo OAI client + service + file picker
├── grobid/                       #   GROBID PDF processing
│   └── tei/                      #     TEI-XML parsing (Jsoup-based extractors)
├── persistence/                  #   Data access layer (shared with PMC S3)
│   ├── entity/                   #     JPA entities (RecordEntity, PaperDocumentEntity, Tracker, etc.)
│   └── repository/               #     Spring Data JPA repositories
└── shared/                       #   Shared OAI contracts
    ├── dto/                      #     Domain DTOs (Record, PaperDocument, OaiPage, etc.)
    ├── util/                     #     Utilities (OaiHttpSupport, LicenseFilter, AuthorNameParser, etc.)
    └── AbstractOaiService.java   #     Template method base for all OAI services

pmcs3/                            # PMC S3 direct integration (separate from OAI pipeline)
├── client/                       #   PmcS3Client — plain HTTPS against the public bucket
├── inventory/                    #   InventoryService / InventoryEntry — daily CSV manifest
├── metadata/                     #   MetadataService / ArticleMetadata — per-article JSON
├── jats/                         #   JatsParser / JatsAuthorExtractor — native JATS → PaperDocument
├── pipeline/                     #   PmcS3Facade, PmcS3ProcessorService, PmcS3LicenseFilter
├── persistence/                  #   PmcS3Tracker entity + repository + service
└── config/                       #   PmcS3RestClientConfig, PmcS3ExecutorConfig (virtual threads)

openai/                           # GPT/LLM operations
├── api/                          #   REST controllers (GPTTransformationController)
├── client/                       #   OpenAI API client (GPTClient, pricing, token counting)
└── estimation/                   #   Cost estimation (ArxivGPTCostEstimator, YoutubeGPTCostEstimator)
```

### Shared Infrastructure Packages

```
embedding/                        # Vector DB (Qdrant) — shared by youtube and oai
├── api/                          #   REST controllers
├── qdrant/                       #   gRPC client, processor, scheduler, health indicator
└── dto/                          #   EmbeddingDto, QdrantChunk

rag/                              # RAG system Python client
├── client/                       #   RestClient + WebFlux clients, embedding orchestration
└── dto/                          #   Request/response DTOs

config/                           # Spring configuration and properties
└── properties/                   #   @ConfigurationProperties beans

shared/                           # Application-wide exceptions
└── exception/                    #   GlobalExceptionHandler + typed exceptions

startup/                          # Application startup tasks
```

## Tech Stack

Java 25, Spring Boot 4.0.1, Gradle, Lombok, Flyway, JPA/Hibernate, WebFlux, Spring Batch, Resilience4j, Jsoup, Apache Tika, jtokkit (token counting), JAXB (PMC OA response mapping).

## Code Quality Standards

All code in this project must follow these principles:

### Readability & Maintainability
- Self-explanatory code with meaningful naming. If logic isn't obvious, add a brief comment.
- Prefer small, focused methods over large procedural blocks.
- Use consistent naming conventions matching existing codebase style.
- Extract magic numbers and repeated literals to named constants.

### Reusability & DRY
- Extract repeated logic into shared methods/utilities (e.g., `OaiHttpSupport`, `AbstractOaiService`).
- Identify recurring patterns and create abstractions (interfaces, base classes, shared DTOs).
- Prefer composition over inheritance (except `AbstractOaiService` template method which is exactly one level deep).

### SOLID Principles
- **Single Responsibility:** Each class/method has one reason to change.
- **Open/Closed:** Extend behavior through new implementations (e.g., new `AbstractOaiService` subclass), not by modifying existing code.
- **Liskov Substitution:** All `OaiSourceHandler` implementations are interchangeable.
- **Interface Segregation:** Keep interfaces focused (e.g., `OaiSourceHandler` has exactly 3 methods).
- **Dependency Inversion:** Depend on abstractions (`OaiSourceHandler`, `GrobidService`) not concrete classes.

### Functional Style (where appropriate)
- Favor pure functions for parsing, filtering, mapping, and validation logic.
- Use streams for collection transformations.
- Prefer immutability — use records for DTOs, avoid shared mutable state.
- Minimize side effects in business logic; isolate I/O at boundaries.

### Error Handling & Resilience
- Per-record failures must not halt batch processing.
- Use Resilience4j `@Retry` + `@RateLimiter` for all external service calls.
- Validate at system boundaries (user input, external APIs), trust internal code.
- Log meaningful context (source ID, URL, attempt count) with warnings/errors.
- Thread safety: `LanguageDetector` access is synchronized; tracker updates use atomic DB operations.

### Configuration
- Operational tuning values (timeouts, delays, model names, pricing) belong in `application.yml`.
- Business-rule constants (license URLs, API contracts) belong as named `static final` fields.
- All OAI source configs follow the same pattern: `oai.{source}.base-url`, `metadata-prefix`, `pagination-delay-ms`.

### Licensing
- Only process papers with licenses that permit commercial use (CC0, CC-BY, CC-BY-SA, MIT, Apache-2.0, BSD). Reject -NC and -ND variants.

## Agent Roster

| Agent | Role | Owns |
|-------|------|------|
| **Lead Architect** | Orchestrates, plans, delegates. Never edits code. | Work decomposition and sequencing |
| **OAI Implementer** | Implements OAI-PMH pipeline code | `oai/arxiv/**`, `oai/zenodo/**`, `oai/pubmed/**`, `oai/pipeline/**`, `oai/shared/**` |
| **GROBID Implementer** | Implements PDF→TEI processing | `oai/grobid/**` |
| **PMC S3 Implementer** | Implements PMC S3 direct pipeline | `pmcs3/**` |
| **Infrastructure Implementer** | Implements persistence, schema, config | Entities, repos, migrations, `application.yml`, `config/**`, shared contracts |
| **Code Reviewer** | Reviews changes post-implementation | Read-only review of any file |
| **Refactoring Agent** | Improves code quality, no logic changes | Any file (behavior-preserving only) |
| **Tester** | Writes and runs tests | `src/test/**` |
| **Manual Tester** | Starts app, monitors logs, reports runtime behavior. Only spawned on explicit user request. | `manual-test-reports/` (output only) |
| **OAI Quality Auditor** | Autonomously audits OAI data quality via read-only DB queries (MCP). Only spawned on explicit user request. | `oai-quality-reports/` (output only) |

Agents must respect ownership boundaries. Cross-boundary changes require coordination through the Lead Architect. See `.claude/agents/` for detailed agent definitions.

Agents must not execute git commands like commit, push, etc. This will be handled by the user