# External Integrations

| Service | Purpose | Key Classes / Config |
|---------|---------|----------------------|
| GROBID (Docker, port 8070) | PDF → structured TEI-XML | `GrobidClient` with retry, `GrobidTeiMapperJsoup` for parsing, options configurable via `grobid.options.*` |
| Qdrant (Docker, ports 6333 / 6334) | Vector database for embeddings | `QdrantGrpcClient` via gRPC, with `@PreDestroy` cleanup |
| Python FastAPI (port 8000) | Embedding generation | `RagSystemRestApiService` / `RagSystemWebFluxClient` |
| OpenAI API | LLM transformations | `GPTClient` / `GptService`; configurable model and pricing (`gpt.model`, `gpt.pricing.*`) |
| Google YouTube Data API v3 | Video / channel metadata and transcripts | `YouTubeGateway` |
| ArXiv OAI | Research paper metadata | `ArxivClient` / `ArxivOaiService` |
| Zenodo OAI | Research paper metadata | `ZenodoClient` / `ZenodoOaiService` |
| PMC OAI | Biomedical paper metadata | `PubmedClient` / `PubmedOaiService` (OA links via JAXB) |
| PMC S3 (AWS Open Data) | Full-text biomedical articles (JATS, plain text, PDF) | `PmcS3Client` / `InventoryService` / `MetadataService` / `JatsParser` — plain HTTPS, no AWS SDK |

## Local services (Docker)

```bash
cd docker && docker compose up -d   # Starts Qdrant, GROBID, Traefik
```

Also required locally:
- PostgreSQL on `localhost:5432`
- Python FastAPI RAG service on `localhost:8000`

## Secrets and API keys

Exported as environment variables (never committed):

- `YOUTUBE_API_KEY`
- `QDRANT_API_KEY`
- `GPT_API_KEY`
