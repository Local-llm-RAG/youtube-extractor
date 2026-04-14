# Pipeline Flows

Detailed flow diagrams for each ingestion and query path.

> The **PMC S3** pipeline flow is owned by the PMC S3 Implementer agent (`.claude/agents/pmc-s3-implementer.md`) because it is self-contained and only that agent maintains it. Look there for the authoritative diagram.

## OAI-PMH Pipeline

```
OAIProcessorService  ->  GenericFacade
  └─ OaiSourceRegistry.get(source)
       └─ {Source}OaiService (extends AbstractOaiService)
            └─ fetchAllRecords() [template method: pagination loop]
                 └─ callListRecords() -> {Source}Client -> OAI-PMH API
                 └─ parseResponse() -> source-specific XML parsing
  └─ Filter: skip already-processed IDs
  └─ Async via oaiExecutor:
       └─ {Source}OaiService.getPdf() -> PDF bytes
       └─ GrobidService.processGrobidDocument() -> PaperDocument
       └─ Language detection (Tika, synchronized)
       └─ PaperInternalService.persistState()
       └─ TrackerService.incrementProcessed() [atomic DB update]
```

Key properties:
- Pagination is driven by OAI resumption tokens inside `AbstractOaiService.fetchAllRecords`.
- Per-record exceptions are caught in `GenericFacade.processOne` and logged — the batch continues.
- Tracker progress is written every N records via `TrackerRepository.incrementProcessed` (atomic DB update).
- License filtering happens before PDF download.

## YouTube Pipeline

```
YouTubeGateway (YouTube Data API v3)
  └─ Channel / video fetch
       └─ Publish VideoDiscoveredEvent
            └─ VideoDiscoveredListener (async)
                 └─ Transcript download (Python service, webshare proxies)
                 └─ Chunking
                 └─ Embedding (RAG FastAPI service)
                 └─ Qdrant upsert + PostgreSQL persistence
```

Key properties:
- Circuit breaker on the transcript API (429 handling, `TranscriptRateLimitedException`).
- Transcript transformation can be GPT-powered with cost estimation upfront.

## Query / RAG Path

```
REST controller
  └─ RagSystemRestApiService / RagSystemWebFluxClient
       └─ Vector / DB lookup
            └─ Cost estimation (jtokkit + pricing table)
            └─ GPTClient transformation
            └─ Response to caller
```

Key properties:
- Cost estimation always runs before any LLM call that charges tokens.
- `EmbeddingProperties` carries defaults for the debug `POST /api/embed` endpoint.
