# Package Structure

Base package: `com.data`. Follows **package-per-feature** architecture.

## Core Feature Packages

```
youtube/                          # YouTube data ingestion
├── api/                          #   REST controllers
├── service/                      #   Business logic (channel fetching, transcript strategies)
├── event/                        #   Async event processing (VideoDiscoveredEvent / Listener)
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
    ├── util/                     #     Utilities (OaiHttpSupport, LicenseFilter, AuthorNameParser, ...)
    └── AbstractOaiService.java   #     Template method base for all OAI services

pmcs3/                            # PMC S3 direct integration (separate from OAI pipeline)
├── client/                       #   PmcS3Client — plain HTTPS against the public bucket
├── inventory/                    #   InventoryService / InventoryEntry — daily CSV manifest
├── metadata/                     #   MetadataService / ArticleMetadata — per-article JSON
├── jats/                         #   JatsParser / JatsAuthorExtractor — native JATS -> PaperDocument
├── pipeline/                     #   PmcS3Facade, PmcS3ProcessorService, PmcS3LicenseFilter
├── persistence/                  #   PmcS3Tracker entity + repository + service
└── config/                       #   PmcS3RestClientConfig, PmcS3ExecutorConfig (virtual threads)

openai/                           # GPT / LLM operations
├── api/                          #   REST controllers (GPTTransformationController)
├── client/                       #   OpenAI API client (GPTClient, pricing, token counting)
└── estimation/                   #   Cost estimation (ArxivGPTCostEstimator, YoutubeGPTCostEstimator)
```

## Shared Infrastructure Packages

```
embedding/                        # Vector DB (Qdrant) — shared by youtube and oai
├── api/                          #   REST controllers
├── qdrant/                       #   gRPC client, processor, scheduler, health indicator
└── dto/                          #   EmbeddingDto, QdrantChunk

rag/                              # RAG system Python client
├── client/                       #   RestClient + WebFlux clients, embedding orchestration
└── dto/                          #   Request / response DTOs

config/                           # Spring configuration and properties
└── properties/                   #   @ConfigurationProperties beans

shared/                           # Application-wide exceptions and shared types
└── exception/                    #   GlobalExceptionHandler + typed exceptions

startup/                          # Application startup tasks
```
