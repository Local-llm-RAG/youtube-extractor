# 1 - PubMed Central (PMC) OAI-PMH Integration

## Overview

Added a new OAI-PMH data source integration for **PubMed Central (PMC)** - the open-access full-text archive of biomedical and life sciences literature hosted by the National Library of Medicine (NLM). This integration follows the exact same Strategy + Registry pattern used by the existing ArXiv and Zenodo integrations.

PMC was chosen over PubMed because PMC provides full-text articles with downloadable PDFs required for GROBID processing, while PubMed only contains citations and abstracts.

## What Was Done

### Step 1: DataSource Enum Extension

**File:** `src/main/java/com/data/oai/DataSource.java`

Added `PUBMED` to the `DataSource` enum alongside `ARXIV` and `ZENODO`. This is the key discriminator used throughout the system by the source registry, tracker, and database persistence layer.

### Step 2: Configuration Properties

**File:** `src/main/java/com/data/config/properties/PubmedOaiProps.java`

Created a `@ConfigurationProperties` record bound to `oai.pubmed` prefix. Properties:
- `baseUrl` - the PMC OAI-PMH endpoint URL
- `metadataPrefix` - the metadata format (`oai_dc` for Dublin Core)
- `set` - the OAI-PMH set for selective harvesting (`pmc-open` for open-access articles)

**File:** `src/main/java/com/data/YoutubeExtractorApplication.java`

Registered `PubmedOaiProps.class` in the `@EnableConfigurationProperties` annotation so Spring Boot binds the YAML properties at startup.

### Step 3: PubmedClient - HTTP Client with Rate Limiting & Retry

**File:** `src/main/java/com/data/oai/pubmed/PubmedClient.java`

Created the HTTP client responsible for all network communication with PMC services. Key features:

- **OAI-PMH ListRecords**: Constructs the OAI-PMH request URL with proper verb, metadataPrefix, from/until dates, set, and resumptionToken parameters. Follows the same pattern as `ArxivClient` and `ZenodoClient`.
- **OA Web Service**: Calls `https://www.ncbi.nlm.nih.gov/pmc/utils/oa/oa.fcgi?id=PMC{id}` to resolve PDF download links for open-access articles.
- **PDF Download**: Downloads PDF bytes from resolved URLs. Automatically converts FTP URLs from NCBI to their HTTPS equivalents (`ftp://ftp.ncbi.nlm.nih.gov/` → `https://ftp.ncbi.nlm.nih.gov/`).
- **Retry with Exponential Backoff**: All HTTP calls go through `executeWithRetry()`:
  - Up to 5 retry attempts on HTTP 429 (Too Many Requests) and HTTP 503 (Service Unavailable)
  - Exponential backoff starting at 1 second, doubling each attempt (1s, 2s, 4s, 8s, 16s)
  - Non-retryable HTTP errors fail immediately
  - Uses the shared `grobidRestClient` RestClient bean with its connection pool

### Step 4: PubmedOaiService - OAI-PMH Parsing & PDF Resolution

**File:** `src/main/java/com/data/oai/pubmed/PubmedOaiService.java`

The core service handling metadata harvesting and PDF retrieval. Key responsibilities:

#### Metadata Harvesting (`getPubmedPapersMetadata`)
- Calls PMC OAI-PMH `ListRecords` verb with `set=pmc-open` to only retrieve open-access articles
- Automatically follows `resumptionToken` pagination (PMC returns 10 records per page)
- Inserts a 350ms delay between pagination calls to stay under PMC's 3 requests/second limit
- Returns filtered list of `Record` DTOs

#### Dublin Core XML Parsing (`parseDublinCore`)
- Uses StAX (Streaming API for XML) event-driven parsing for memory efficiency
- State machine approach with `inHeader`/`inMetadata` flags, matching the ArXiv/Zenodo pattern
- Dublin Core element mapping:
  - `dc:creator` → Author objects (parsed from "LastName, FirstName" or "FirstName LastName" format)
  - `dc:subject` → categories list
  - `dc:description` → comments (abstract text, accumulated across multiple elements)
  - `dc:identifier` → routed to DOI field (if DOI pattern matched) via regex
  - `dc:rights` → license
  - `dc:source` → journalRef
  - OAI header `identifier` → oaiIdentifier (format: `oai:pubmedcentral.nih.gov:{numericId}`)
  - OAI header `datestamp` → datestamp

#### Record Filtering
Two-stage filtering applied to each parsed record:
1. **License filter** (`isOpenAccessLicense`): Accepts CC0, CC-BY (all versions), MIT, Apache-2.0, BSD-2/3-clause. Rejects -NC, -ND, -SA variants. Follows the same philosophy as ArXiv and Zenodo license filters.
2. **Scholarly text filter** (`isLikelyScholarlyText`): Requires at least one author. Simpler than Zenodo's filter since PMC `pmc-open` set already pre-filters to scholarly articles.

#### PDF Resolution (`getPdf`)
Two-tier approach for obtaining PDFs:
1. **Primary**: Calls the PMC OA Web Service (`oa.fcgi`) to get the official PDF download link. Parses the response XML to extract the `<link format="pdf" href="..."/>` element.
2. **Fallback**: If OA service fails or returns no PDF link, constructs a direct URL: `https://pmc.ncbi.nlm.nih.gov/articles/PMC{id}/pdf/`

This ensures GROBID receives proper PDF files for structured text extraction.

### Step 5: PubmedSourceHandler - Strategy Pattern Implementation

**File:** `src/main/java/com/data/oai/generic/PubmedSourceHandler.java`

Implements the `OaiSourceHandler` interface, completing the Strategy pattern:
- `supports()` returns `DataSource.PUBMED`
- `fetchMetadata()` delegates to `PubmedOaiService.getPubmedPapersMetadata()`
- `fetchPdfAndEnrich()` delegates to `PubmedOaiService.getPdf()`

The handler is auto-registered as a Spring `@Component` and automatically picked up by `OaiSourceRegistry` via its `List<OaiSourceHandler>` injection.

### Step 6: Application Configuration Update

**File:** `src/main/resources/application.yml`

Updated the PMC OAI-PMH base URL from the deprecated endpoint to the new official one:
- **Before:** `https://www.ncbi.nlm.nih.gov/pmc/oai/oai.cgi` (old, redirects)
- **After:** `https://pmc.ncbi.nlm.nih.gov/api/oai/v1/mh/` (current official endpoint)

Configuration remains:
```yaml
oai:
  pubmed:
    base-url: "https://pmc.ncbi.nlm.nih.gov/api/oai/v1/mh/"
    metadata-prefix: oai_dc
    set: pmc-open
```

### Step 7: Batch Processor Update

**File:** `src/main/java/com/data/startup/OAIProcessorService.java`

Added `DataSource.PUBMED` to the batch processing list alongside `DataSource.ARXIV`. The processor now iterates over both sources when running the 90-day lookback job:
```java
List.of(DataSource.ARXIV, DataSource.PUBMED)
```

The existing `GenericFacade` orchestration (GROBID processing, language detection, persistence) works without modification since it operates through the `OaiSourceHandler` abstraction.

## Architecture Fit

The integration plugs into the existing pipeline without modifying any shared code:

```
OAIProcessorService
  └─ GenericFacade
       └─ OaiSourceRegistry.get(PUBMED)
            └─ PubmedSourceHandler
                 └─ PubmedOaiService  ──→  PubmedClient  ──→  PMC OAI-PMH API
                                                            ──→  PMC OA Web Service
                                                            ──→  PDF Download
       └─ GrobidService (unchanged)
       └─ PaperInternalService (unchanged)
```

## Key Design Decisions

1. **Dublin Core (`oai_dc`) over JATS (`pmc_fm`)**: Simpler to parse, sufficient metadata for the Record DTO, and universally available for all PMC articles.
2. **`pmc-open` set filtering**: Only harvests open-access articles that have redistributable PDFs, ensuring GROBID compatibility.
3. **OA Web Service for PDF links**: More reliable than constructing URLs directly. The service explicitly tells us which articles have PDFs and where they are hosted.
4. **FTP-to-HTTPS conversion**: NCBI's OA service returns FTP links, but the same files are available via HTTPS at the same path. This avoids FTP client dependencies.
5. **350ms pagination delay**: Keeps request rate under PMC's 3 req/s limit without being overly conservative.
6. **5-retry exponential backoff**: Handles transient rate limits (429) and service unavailability (503) gracefully, scaling from 1s to 16s between retries.

## Files Changed

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/com/data/oai/DataSource.java` | Modified | Added `PUBMED` enum value |
| `src/main/java/com/data/config/properties/PubmedOaiProps.java` | Created | Configuration properties for PMC OAI-PMH |
| `src/main/java/com/data/oai/pubmed/PubmedClient.java` | Created | HTTP client with retry and rate limit handling |
| `src/main/java/com/data/oai/pubmed/PubmedOaiService.java` | Created | OAI-PMH parsing, filtering, and PDF resolution |
| `src/main/java/com/data/oai/generic/PubmedSourceHandler.java` | Created | OaiSourceHandler implementation for PUBMED |
| `src/main/java/com/data/YoutubeExtractorApplication.java` | Modified | Registered PubmedOaiProps in @EnableConfigurationProperties |
| `src/main/resources/application.yml` | Modified | Updated PMC base URL to new official endpoint |
| `src/main/java/com/data/startup/OAIProcessorService.java` | Modified | Added PUBMED to batch processing source list |
