# Project Documentation

Reference documentation for the data extraction and processing platform. `CLAUDE.md` at the repository root contains the always-loaded project rules; everything in this folder is deeper reference material that agents and contributors can pull in on demand.

## Index

| Document | Contents |
|----------|----------|
| [architecture.md](./architecture.md) | Core architectural patterns: Template Method + Strategy, Facade, event-driven, Spring Batch, resilience |
| [pipelines.md](./pipelines.md) | Detailed pipeline flow diagrams (OAI-PMH, YouTube, Query/RAG). PMC S3 flow lives in the PMC S3 Implementer agent definition. |
| [integrations.md](./integrations.md) | External service integrations: GROBID, Qdrant, FastAPI RAG, OpenAI, YouTube, OAI sources, PMC S3 |
| [rest-api.md](./rest-api.md) | REST API endpoint catalogue |
| [package-structure.md](./package-structure.md) | Full `com.data.*` package tree with per-package purpose |
| [database.md](./database.md) | Schema conventions, key column names, `DataSource` enum storage details |
| [design-decisions.md](./design-decisions.md) | Intentional behaviors that look like bugs: tracker-counts-attempts, in-memory per-day harvest, blank-title fallback, license filter rejectND posture |

## Ownership

Documentation in this folder is owned by the **Documentation Agent** (`.claude/agents/documentation.md`). The Lead Architect triggers that agent when architecture, pipelines, integrations, or agent definitions change in ways that invalidate existing docs. See the Documentation Agent definition for the full trigger checklist.

## Conventions

- One topic per file. Cross-link rather than duplicate.
- Code references use backticks and fully qualified class names where useful (e.g. `com.data.oai.pipeline.GenericFacade`).
- Keep enforcement rules (migration rules, mandatory routing, licensing) in `CLAUDE.md`, not here. This folder is reference, not policy.
- Nothing in this folder overrides `CLAUDE.md`. If the two disagree, `CLAUDE.md` wins and the doc must be updated.
