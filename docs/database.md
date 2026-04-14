# Database Reference

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`. JPA runs with Hibernate in `validate` mode — schema changes require a new migration file.

> **Migration rules (mandatory idempotency) are kept in `CLAUDE.md` because they are enforcement policy, not reference material.** This file is reference only: current column names, enum conventions, and shape notes.

## `DataSource` enum

`DataSource` lives in `com.data.shared`. It is persisted as `VARCHAR` (not a Postgres enum), so new Java enum values require **no migration**.

Current values:
- `ARXIV`
- `ZENODO`
- `PUBMED`
- `PMC_S3`

## Key column names (set by V24)

- `source_record.external_identifier` — upstream provider id.
  - OAI identifier for ArXiv / Zenodo / PubMed.
  - PMID for PMC S3.
  - Was previously `oai_identifier`.
- `record_document.source_xml` — raw structured XML.
  - TEI for OAI sources.
  - JATS for PMC S3.
  - Was previously `tei_xml`.
- `record_document.funding_list` — `text[]` of funding statements. Currently populated only by the PMC S3 pipeline from `<funding-group>/<award-group>`.
- `record_author.orcid` — `VARCHAR(64)` holding the ORCID iD where supplied. PMC S3 reads this from JATS `<contrib-id>`.

## Tracker tables

- OAI tracker: `(date_start, data_source)` uniqueness. Processed count is updated every 10 records by `GenericFacade`.
- PMC S3 tracker: `pmcs3/persistence/PmcS3Tracker` — manifest-keyed with COMPLETED / FAILED terminal states.

## Write path

All paper data writes go through `PaperInternalService.persistState()` — the **sole** write path. Maintain transactional boundaries in any code that touches it.
