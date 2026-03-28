# Package Structure

Base package: `com.data`

This project follows a **package-per-feature** architecture. The three core feature packages (`youtube`, `oai`, `openai`) are as independent as possible. Shared infrastructure packages (`embedding`, `rag`, `config`, `shared`, `startup`) provide cross-cutting capabilities.

---

## Core Feature Packages

### `youtube` — YouTube Data Ingestion

Fetches video metadata via YouTube API, downloads transcripts through a Python service, and stores everything in PostgreSQL. Supports event-driven async processing.

```
youtube/
├── api/                         # REST controllers
│   └── YouTubeChannelVideosController
├── service/                     # Business logic
│   ├── YouTubeChannelVideosService    # Orchestrates channel/video fetching
│   ├── YouTubeClientService           # YouTube API interaction
│   ├── YouTubeGateway                 # Low-level YouTube Data API wrapper
│   ├── YouTubeInternalService         # Internal persistence operations
│   ├── YoutubeRegionBootstrapService  # Bootstraps region/language data
│   └── YoutubeTranscriptFetchStrategy # Enum for transcript fetch modes
├── event/                       # Async event processing
│   ├── VideoDiscoveredEvent           # Published when a new video is found
│   └── VideoDiscoveredListener        # Handles transcript download on discovery
└── persistence/
    ├── entity/                  # JPA entities
    │   ├── ChannelEntity, Video, VideoTranscript
    │   ├── YouTubeRegion, YouTubeRegionLanguage
    └── repository/              # Spring Data JPA repositories
        ├── ChannelRepository, VideoRepository, VideoTranscriptRepository
        └── YouTubeRegionRepository, YouTubeRegionLanguageRepository
```

**Dependencies:** `embedding` (for vectorization), `rag` (for transcript embedding requests)

---

### `oai` — OAI-PMH Research Paper Harvesting

Harvests metadata from ArXiv, Zenodo, and PubMed Central using the OAI-PMH protocol. Downloads PDFs, processes them through GROBID for structured extraction, detects language, and persists enriched data.

```
oai/
├── pipeline/                    # Pipeline orchestration
│   ├── OAIProcessorService            # Spring Batch job runner (configurable days-back + sources)
│   ├── GenericFacade                  # Coordinates fetch → filter → download → GROBID → persist
│   ├── OaiSourceHandler               # Strategy interface for data sources
│   ├── OaiSourceRegistry              # Resolves handler by DataSource enum
│   └── DataSource                     # Enum: ARXIV, ZENODO, PUBMED
├── arxiv/                       # ArXiv OAI client + service
│   ├── ArxivClient                    # HTTP client (uses OaiHttpSupport)
│   ├── ArxivOaiService                # Extends AbstractOaiService; ArXiv XML parsing
│   └── search/                  # ArXiv search REST API
│       ├── ArxivController            # POST /api/arxiv/search
│       ├── ArxivService               # Search orchestration (triggers pipeline)
│       ├── ArxivSearchRequest         # Request DTO
│       ├── ArxivIdExtractor           # Extracts IDs from ArXiv URLs
│       └── Paper                      # Response DTO
├── pubmed/                      # PubMed Central OAI client + service
│   ├── PubmedClient                   # HTTP client (uses OaiHttpSupport)
│   ├── PubmedOaiService               # Extends AbstractOaiService; Dublin Core parsing
│   └── oa/                      # JAXB model for PMC OA Web Service
│       ├── OaResponse, OaRecords, OaRecord, OaLink
├── zenodo/                      # Zenodo OAI client + service
│   ├── ZenodoClient                   # HTTP client (uses OaiHttpSupport)
│   ├── ZenodoOaiService               # Extends AbstractOaiService; DataCite parsing
│   ├── ZenodoRecordFilePicker         # Selects best PDF from Zenodo files
│   ├── ZenodoJson, ZenodoRecord       # Zenodo API response models
├── grobid/                      # PDF processing via GROBID
│   ├── GrobidClient                   # HTTP client to GROBID service
│   ├── GrobidService                  # Orchestrates PDF → PaperDocument
│   └── tei/                     # TEI-XML parsing internals
│       ├── GrobidTeiMapperJsoup       # Maps TEI-XML to PaperDocument DTO
│       ├── GrobidReferenceExtractor   # Extracts references from TEI
│       ├── GrobidSectionExtractor     # Extracts sections from TEI
│       ├── GrobidTextExtractor        # Extracts plain text from TEI
│       └── GrobidTeiUtils             # Shared TEI parsing utilities
├── persistence/                 # Data access layer
│   ├── entity/                  # JPA entities
│   │   ├── RecordEntity               # Core OAI record (metadata)
│   │   ├── RecordAuthorEntity         # Author linked to a record
│   │   ├── PaperDocumentEntity        # Full paper document (sections, refs)
│   │   ├── SectionEntity              # Paper section (abstract, body, etc.)
│   │   ├── EmbedTranscriptChunkEntity # Embedding chunk for a section
│   │   ├── ReferenceMentionEntity     # Reference mention in a paper
│   │   └── Tracker                    # Tracks processing progress per period
│   ├── repository/              # Spring Data JPA repositories
│   │   ├── RecordRepository, RecordSearchRepository
│   │   ├── RecordAuthorRepository, PaperDocumentRepository
│   │   ├── SectionRepository, EmbedTranscriptChunkRepository
│   │   └── TrackerRepository
│   ├── PaperInternalService           # Persistence orchestration (record → document → sections)
│   ├── TrackerService                 # Tracker CRUD operations
│   └── SectionFilter                  # Query helper for section filtering
├── shared/                      # Shared OAI contracts
│   ├── AbstractOaiService             # Template method base for all OAI source services
│   ├── dto/                     # Domain transfer objects
│   │   ├── Record                     # Parsed OAI record (pre-persistence)
│   │   ├── OaiPage                    # Single page of OAI ListRecords results
│   │   ├── Author                     # Author name model
│   │   ├── PaperDocument              # Full parsed paper with sections/refs
│   │   ├── Section, Reference         # Paper section and reference models
│   │   └── PdfContent                 # Downloaded PDF bytes + metadata
│   └── util/                    # Stateless utilities
│       ├── OaiHttpSupport             # Shared HTTP utilities (URI building, exchange, retryable)
│       ├── AuthorNameParser           # Parses "Last, First" / "First Last"
│       ├── DateParser                 # Date string parsing
│       ├── DoiNormalizer              # Normalizes DOI formats
│       ├── LicenseFilter              # Filters for commercial-use licenses
│       └── XmlFactories               # Thread-safe XML parser factories
└── RetryableApiException        # Retryable exception for OAI API calls
```

**Dependencies:** `config` (for properties), `embedding` (for EmbeddingDto), `shared` (for exceptions)

---

### `openai` — GPT/LLM Operations

Provides GPT-powered text transformation, cost estimation for ArXiv papers and YouTube transcripts, and OpenAI API integration.

```
openai/
├── GptService                         # Orchestrates cost estimation + transformations
├── api/                         # REST controllers
│   └── GPTTransformationController    # POST /api/estimate/*
├── client/                      # OpenAI API client
│   ├── GPTClient                      # HTTP client to OpenAI API
│   ├── GPTTextResult                  # Response record (text + token usage)
│   ├── GPTTaskPriceMultiplier         # Enum for task-specific price multipliers
│   └── TokenUsage                     # Token count record
└── estimation/                  # Cost estimation logic
    ├── ArxivGPTCostEstimator          # Estimates GPT cost for paper sections
    ├── YoutubeGPTCostEstimator        # Estimates GPT cost for video transcripts
    ├── CostEstimate                   # Cost estimate result record
    └── LangText                       # Language-tagged text helper
```

**Dependencies:** `oai.persistence` (for record/section data), `youtube.persistence` (for video data), `config` (for GPT properties)

---

## Shared Infrastructure Packages

### `embedding` — Vector Database (Qdrant)

Manages vector embeddings and Qdrant gRPC operations. Shared by both `youtube` and `oai` features.

```
embedding/
├── api/                         # REST controllers
│   └── EmbeddingController            # Embedding endpoints
├── qdrant/                      # Qdrant vector DB operations
│   ├── QdrantGrpcClient               # gRPC client to Qdrant
│   ├── QdrantProcessorService         # Processes embedding batches
│   ├── QdrantScheduler                # Scheduled embedding tasks
│   └── QdrantHealthIndicator          # Spring Boot health check for Qdrant
└── dto/                         # Data transfer objects
    ├── EmbeddingDto                   # Embedding request/response model
    └── QdrantChunk                    # Vector chunk for Qdrant storage
```

### `rag` — RAG System Client

Clients for the external Python FastAPI RAG service (embedding generation, chat, transcript processing).

```
rag/
├── client/                      # Service clients
│   ├── RagSystemRestApiClient         # RestClient-based sync client
│   ├── RagSystemWebFluxClient         # WebFlux-based reactive client
│   └── RagSystemRestApiService        # Higher-level embedding orchestration
└── dto/                         # Request/response DTOs
    ├── EmbedTranscriptRequest, EmbedTranscriptResponse
    ├── ChatRequest, ChatResponse
    ├── EmbeddingTask, TranscriptResponse
```

### `config` — Spring Configuration

All `@Configuration` classes and `@ConfigurationProperties` for the application.

```
config/
├── AsyncConfig, OaiExecutorConfig, GrobidRestClientConfig
├── OpenApiConfig, RagSystemConfig, TikaLangDetectConfig
├── YouTubeConfig, SemaphoreAsyncTaskExecutor
└── properties/                  # @ConfigurationProperties beans
    ├── ArxivOaiProps, ArxivSearchProperties
    ├── EmbeddingProperties, GptProperties, GrobidProperties
    ├── ModelProperties, OaiProcessingProperties
    ├── PubmedOaiProps, QdrantGrpcConfig
    ├── RagSystemClientProperties, ZenodoOaiProps
```

### `shared` — Shared Exceptions

Application-wide exception hierarchy used across all features.

```
shared/
└── exception/
    ├── ApplicationException           # Base exception
    ├── GlobalExceptionHandler         # @ControllerAdvice
    ├── CostEstimationException, GrobidProcessingException
    ├── OaiHarvestException, OaiParseException
    ├── PdfDownloadException, QdrantOperationException
    ├── ResourceNotFoundException, TranscriptRateLimitedException
    └── UnsupportedDataSourceException
```

### `startup` — Application Startup

Bootstrap tasks that run on application startup.

```
startup/
├── OnInitialStartup                   # Bootstraps YouTube region data
└── PostgresAdvisoryLock               # Distributed lock for batch jobs
```

---

## Dependency Graph (Feature Packages)

```
youtube ──────► embedding ◄────── oai
    │               │               │
    │               ▼               │
    │              rag              │
    │                               │
    ▼                               ▼
  config ◄──────────────────────  config
    │                               │
    ▼                               ▼
  shared                          shared
```

- `youtube` and `oai` are independent of each other
- Both depend on `embedding` and `rag` for vectorization
- `openai` reads from both `youtube.persistence` and `oai.persistence`
- All features depend on `config` (properties) and `shared` (exceptions)

---

## Key Architectural Patterns

| Pattern | Where | Purpose |
|---------|-------|---------|
| Template Method + Strategy | `AbstractOaiService` + `OaiSourceHandler` + `OaiSourceRegistry` | Shared pagination loop with pluggable parsing/PDF resolution per source |
| Shared HTTP utilities | `OaiHttpSupport.executeOaiExchange` | Eliminates duplicated exchange handlers across OAI clients |
| Facade | `oai/pipeline/GenericFacade` | Coordinates multi-step OAI processing pipeline |
| Event-driven | `youtube/event/` | Async transcript download on video discovery |
| Entity/Repository split | `*/persistence/entity/` + `*/persistence/repository/` | Clear separation of JPA entities from data access |
| DTO/Util split | `oai/shared/dto/` + `oai/shared/util/` | Separates data models from stateless utilities |
| TEI extraction layer | `oai/grobid/tei/` | Isolates complex XML parsing from service orchestration |
| JAXB mapping | `oai/pubmed/oa/` | Declarative XML-to-object binding for PMC OA Web Service |
