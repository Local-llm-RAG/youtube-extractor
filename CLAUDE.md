# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 4.0.1 data extraction and processing platform that aggregates research papers (ArXiv, Zenodo) and YouTube videos, enriches them with AI-powered embeddings, and provides RAG (Retrieval-Augmented Generation) capabilities via a vector database (Qdrant).

## Build & Run Commands

```bash
# Build
./gradlew build

# Run application (requires PostgreSQL, env vars, and Docker services)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.demo.YoutubeExtractorApplicationTests"
```

**Required environment variables:** `YOUTUBE_API_KEY`, `QDRANT_API_KEY`

**Local services (Docker):**
```bash
cd docker && docker compose up -d   # Starts Qdrant, GROBID, Traefik
```
Also requires: PostgreSQL on localhost:5432, Python FastAPI RAG service on localhost:8000.

## Architecture

**Base package:** `com.data`

### Data Processing Pipelines

1. **ArXiv/Zenodo Path:** OAI protocol query → fetch metadata → download PDF → GROBID parse to TEI-XML → extract sections → language detect (Tika) → embed → store in Qdrant
2. **YouTube Path:** YouTube API fetch → download transcripts → chunk → embed → store in Qdrant
3. **Query Path:** Search → vector/database lookup → cost estimation → LLM transformation

### Key Architectural Patterns

- **Strategy + Registry for OAI sources:** `OaiSourceHandler` interface with `ArxivSourceHandler`/`ZenodoSourceHandler` implementations, resolved via `OaiSourceRegistry` by `DataSource` enum.
- **Facade:** `GenericFacade` coordinates the full OAI processing pipeline (fetch → parse → embed → store).
- **Event-driven:** `VideoDiscoveredEvent` / `VideoDiscoveredListener` for async YouTube processing.
- **Spring Batch:** `OAIProcessorService` runs batch jobs for processing 90 days of ArXiv papers, with `PostgresAdvisoryLock` for distributed locking.
- **Resilience4j:** Circuit breaker on transcript API (429 handling), rate limiter (1 req/12s), exponential backoff retry.

### External Integrations

| Service | Purpose | Config |
|---------|---------|--------|
| GROBID (Docker, port 8070) | PDF → structured TEI-XML | `GrobidClient` with retry, `GrobidTeiMapperJsoup` for parsing |
| Qdrant (Docker, ports 6333/6334) | Vector database for embeddings | `QdrantGrpsClient` via GRPC |
| Python FastAPI (port 8000) | Embedding generation | `RagSystemRestApiService` / `RagSystemWebFluxClient` |
| OpenAI API | LLM transformations | `GPTClient` / `GptService` with cost estimation |
| Google YouTube Data API v3 | Video/channel metadata & transcripts | `YouTubeGateway` |
| ArXiv OAI / Zenodo OAI | Research paper metadata | Protocol clients in `com.data.oai` |

### REST API Endpoints

- `POST /api/arxiv/search` — Search ArXiv papers
- `GET /api/channels/mass` — Fetch videos from multiple channels
- `GET /api/channels/videos` — Fetch single channel videos
- `GET /api/channels/video` — Fetch single video by URL
- `POST /api/estimate/youtube`, `/youtube/channel`, `/arxiv` — GPT cost estimation
- Swagger UI at `/swagger`

### Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`. JPA with Hibernate in validate mode — schema changes require new migration files.

## Tech Stack

Java 25, Spring Boot 4.0.1, Gradle, Lombok, Flyway, JPA/Hibernate, WebFlux, Spring Batch, Resilience4j, Jsoup, Apache Tika, jtokkit (token counting).
