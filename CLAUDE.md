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

- **Strategy + Registry:** `OaiSourceHandler` interface with `ArxivSourceHandler`, `ZenodoSourceHandler`, and `PubmedSourceHandler` implementations, resolved via `OaiSourceRegistry` by `DataSource` enum. Adding a new source means implementing the interface and registering as a Spring `@Component`.
- **Facade:** `GenericFacade` coordinates the full OAI processing pipeline (fetch → filter → download → GROBID → language detect → persist). Per-record failures are isolated — one failure never halts the batch.
- **Event-driven:** `VideoDiscoveredEvent` / `VideoDiscoveredListener` for async YouTube processing.
- **Spring Batch:** `OAIProcessorService` runs batch jobs processing 90 days of papers with `PostgresAdvisoryLock` for distributed locking.
- **Resilience:** Circuit breaker on transcript API (429 handling), rate limiter (1 req/12s), exponential backoff retry (Resilience4j). PMC client has semaphore-based throttling for NCBI rate limits.

### Pipeline Flow (OAI Sources)

```
OAIProcessorService  →  GenericFacade
  └─ OaiSourceRegistry.get(source)
       └─ {Source}SourceHandler.fetchMetadata()
            └─ {Source}OaiService  →  {Source}Client  →  OAI-PMH API
  └─ Filter: skip already-processed IDs
  └─ Async via grobidExecutor:
       └─ {Source}SourceHandler.fetchPdfAndEnrich()  →  PDF bytes
       └─ GrobidService.processGrobidDocument()  →  PaperDocument
       └─ Language detection (Tika)
       └─ PaperInternalService.persistState()
```

### External Integrations

| Service | Purpose | Config |
|---------|---------|--------|
| GROBID (Docker, port 8070) | PDF → structured TEI-XML | `GrobidClient` with retry, `GrobidTeiMapperJsoup` for parsing |
| Qdrant (Docker, ports 6333/6334) | Vector database for embeddings | `QdrantGrpsClient` via GRPC |
| Python FastAPI (port 8000) | Embedding generation | `RagSystemRestApiService` / `RagSystemWebFluxClient` |
| OpenAI API | LLM transformations | `GPTClient` / `GptService` with cost estimation |
| Google YouTube Data API v3 | Video/channel metadata & transcripts | `YouTubeGateway` |
| ArXiv OAI | Research paper metadata | `ArxivClient` / `ArxivOaiService` |
| Zenodo OAI | Research paper metadata | `ZenodoClient` / `ZenodoOaiService` |
| PMC OAI | Biomedical paper metadata | `PubmedClient` / `PubmedOaiService` |

### REST API Endpoints

- `POST /api/arxiv/search` — Search ArXiv papers
- `GET /api/channels/mass` — Fetch videos from multiple channels
- `GET /api/channels/videos` — Fetch single channel videos
- `GET /api/channels/video` — Fetch single video by URL
- `POST /api/estimate/youtube`, `/youtube/channel`, `/arxiv` — GPT cost estimation
- Swagger UI at `/swagger`

### Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`. JPA with Hibernate in validate mode — schema changes require new migration files. `DataSource` is stored as `VARCHAR(128)` (not a Postgres enum), so new Java enum values need no migration.

## Tech Stack

Java 25, Spring Boot 4.0.1, Gradle, Lombok, Flyway, JPA/Hibernate, WebFlux, Spring Batch, Resilience4j, Jsoup, Apache Tika, jtokkit (token counting).

## Code Quality Standards

All code in this project must follow these principles:

### Readability & Maintainability
- Self-explanatory code with meaningful naming. If logic isn't obvious, add a brief comment.
- Prefer small, focused methods over large procedural blocks.
- Use consistent naming conventions matching existing codebase style.

### Reusability & DRY
- Extract repeated logic into shared methods/utilities.
- Identify recurring patterns and create abstractions (interfaces, base classes, shared DTOs).
- Prefer composition over inheritance.

### SOLID Principles
- **Single Responsibility:** Each class/method has one reason to change.
- **Open/Closed:** Extend behavior through new implementations (e.g., new `OaiSourceHandler`), not by modifying existing code.
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
- Use retry with backoff for external service calls.
- Validate at system boundaries (user input, external APIs), trust internal code.
- Log meaningful context (source ID, URL, attempt count) with warnings/errors.

### Licensing
- Only process papers with licenses that permit commercial use (CC0, CC-BY, CC-BY-SA, MIT, Apache-2.0, BSD). Reject -NC and -ND variants.

## Agent Collaboration Model

This project uses a multi-agent system with clear ownership boundaries:

- **Lead Architect** — Plans, sequences, and delegates work. Never edits code directly.
- **OAI Implementer** — Owns OAI-PMH handlers, clients, services, and `GenericFacade`.
- **GROBID Implementer** — Owns PDF→TEI processing and DTO mapping.
- **Infrastructure Implementer** — Owns persistence, schema, config, and shared contracts.
- **Code Reviewer** — Reviews for correctness, clean code, SOLID adherence, and architectural fit.
- **Refactoring Agent** — Improves code quality without changing business logic.
- **Tester** — Writes and maintains unit/integration tests.

Agents must respect ownership boundaries. Cross-boundary changes require coordination through the Lead Architect. See individual agent definitions in `.claude/agents/` for detailed responsibilities.
