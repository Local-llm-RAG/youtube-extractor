# Architecture

Core architectural patterns used across the platform. For per-pipeline flow diagrams see [pipelines.md](./pipelines.md).

## Base package

`com.data` — follows **package-per-feature** layout. See [package-structure.md](./package-structure.md) for the full tree.

## Core Architectural Patterns

### Template Method + Strategy (OAI sources)

`AbstractOaiService` provides the shared OAI-PMH pagination loop and implements `OaiSourceHandler`. Each source (`ArxivOaiService`, `ZenodoOaiService`, `PubmedOaiService`) extends it with source-specific parsing and PDF resolution. Handlers are resolved via `OaiSourceRegistry` keyed by the `DataSource` enum.

Adding a new OAI source means exactly one client class plus one service extending `AbstractOaiService` — no changes to the pipeline code.

### Shared HTTP utilities

`OaiHttpSupport` provides common OAI-PMH URI building, retryable error detection, and exchange handling. Each OAI client delegates to `executeOaiExchange`, supplying source-specific quirks via an `IntPredicate`.

### Facade

`GenericFacade` coordinates the full OAI processing pipeline: fetch metadata, filter, download PDF, invoke GROBID, detect language, persist. Per-record failures are isolated — one failure never halts the batch. Tracker progress is updated atomically at the DB level via `TrackerRepository.incrementProcessed`.

`PmcS3Facade` plays the equivalent role for the PMC S3 direct pipeline.

### Event-driven (YouTube)

`VideoDiscoveredEvent` / `VideoDiscoveredListener` decouple video discovery from transcript processing. Listeners consume events asynchronously so that discovery can proceed without waiting on transcript fetches.

### Spring Batch

`OAIProcessorService` runs the OAI harvest as a Spring Batch job with configurable `daysBack` and `sources` list (`oai.processing.*` in `application.yml`). It acquires a `PostgresAdvisoryLock` to prevent concurrent runs across instances.

`PmcS3ProcessorService` runs on its own schedule with the same advisory-lock pattern.

### Resilience

- All OAI clients use Resilience4j `@Retry` + `@RateLimiter` annotations.
- Circuit breaker on the transcript API (429 handling, `TranscriptRateLimitedException` only).
- Exponential backoff retry with parameters configurable in `application.yml`.
- Thread safety: `LanguageDetector` access is synchronized; tracker updates use atomic DB operations.

## Data Processing Paths (overview)

1. **OAI-PMH Path** — OAI query → metadata → PDF → GROBID → TEI-XML → language detect (Tika) → embed → persist.
2. **PMC S3 Path** — Daily inventory manifest → license filter → JSON metadata → JATS XML + .txt → native JATS parse → language from `xml:lang` → persist.
3. **YouTube Path** — YouTube API → transcript download → chunk → embed → Qdrant.
4. **Query Path** — Search → vector / DB lookup → cost estimation → LLM transformation.

Each path is detailed in [pipelines.md](./pipelines.md) (except PMC S3, whose flow is owned by the PMC S3 Implementer agent).
