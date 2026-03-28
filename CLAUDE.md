# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 4.0.1 data extraction and processing platform that aggregates research papers (ArXiv, Zenodo, PubMed Central) and YouTube videos, enriches them with AI-powered embeddings on demand, and provides RAG (Retrieval-Augmented Generation) capabilities via a vector database (Qdrant).

The project collects data from multiple sources:
1. **YouTube** — Fetches video metadata via YouTube API, downloads transcripts through a Python service (using youtube-transcript-api with webshare proxies), stores everything in PostgreSQL. Supports GPT-powered transcript transformation with cost estimation.
2. **Research Papers (OAI-PMH)** — Harvests metadata from ArXiv, Zenodo, and PubMed Central using the OAI-PMH protocol. Downloads PDFs, processes them through GROBID for structured extraction, detects language, and persists enriched data.
3. **Query/RAG** — Searches stored data via vector/database lookup, estimates LLM costs, and performs GPT transformations.

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
2. **YouTube Path:** YouTube API fetch → download transcripts → chunk → embed → store in Qdrant
3. **Query Path:** Search → vector/database lookup → cost estimation → LLM transformation

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
  └─ Async via grobidExecutor:
       └─ {Source}OaiService.getPdf() → PDF bytes
       └─ GrobidService.processGrobidDocument() → PaperDocument
       └─ Language detection (Tika, synchronized)
       └─ PaperInternalService.persistState()
       └─ TrackerService.incrementProcessed() [atomic DB update]
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

### REST API Endpoints

- `POST /api/arxiv/search` — Search ArXiv papers
- `GET /api/channels/mass` — Fetch videos from multiple channels
- `GET /api/channels/videos` — Fetch single channel videos
- `GET /api/channels/video` — Fetch single video by URL
- `POST /api/estimate/youtube`, `/youtube/channel`, `/arxiv` — GPT cost estimation
- `POST /api/embed` — Debug embedding endpoint (uses `EmbeddingProperties` defaults)
- Swagger UI at `/swagger`

### Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`. JPA with Hibernate in validate mode — schema changes require new migration files. `DataSource` is stored as `VARCHAR(128)` (not a Postgres enum), so new Java enum values need no migration.

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
│                                 #     OaiSourceHandler, OaiSourceRegistry, DataSource)
├── arxiv/                        #   ArXiv OAI client + service
│   └── search/                   #     ArXiv search REST API
├── pubmed/                       #   PubMed OAI client + service
│   └── oa/                       #     JAXB model for PMC OA Web Service
├── zenodo/                       #   Zenodo OAI client + service + file picker
├── grobid/                       #   GROBID PDF processing
│   └── tei/                      #     TEI-XML parsing (Jsoup-based extractors)
├── persistence/                  #   Data access layer
│   ├── entity/                   #     JPA entities (RecordEntity, PaperDocumentEntity, Tracker, etc.)
│   └── repository/               #     Spring Data JPA repositories
└── shared/                       #   Shared OAI contracts
    ├── dto/                      #     Domain DTOs (Record, PaperDocument, OaiPage, etc.)
    ├── util/                     #     Utilities (OaiHttpSupport, LicenseFilter, AuthorNameParser, etc.)
    └── AbstractOaiService.java   #     Template method base for all OAI services

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

## Agent Collaboration Model

This project uses a multi-agent system with clear ownership boundaries:

- **Lead Architect** — Plans, sequences, and delegates work. Never edits code directly.
- **OAI Implementer** — Owns OAI-PMH services (extending `AbstractOaiService`), clients, `GenericFacade`, and `OaiHttpSupport`.
- **GROBID Implementer** — Owns PDF→TEI processing and DTO mapping in `oai/grobid/`.
- **Infrastructure Implementer** — Owns persistence, schema, config, and shared contracts.
- **Code Reviewer** — Reviews for correctness, clean code, SOLID adherence, and architectural fit.
- **Refactoring Agent** — Improves code quality without changing business logic.
- **Tester** — Writes and maintains unit/integration tests.

Agents must respect ownership boundaries. Cross-boundary changes require coordination through the Lead Architect. See individual agent definitions in `.claude/agents/` for detailed responsibilities.
