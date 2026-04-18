# Design Decisions

Intentional behaviors and trade-offs that are easy to mistake for bugs. Each entry documents a decision that was deliberate at some point, explains the reasoning, and lists the conditions under which the decision should be revisited.

This is a **reference** document, not policy. Enforcement rules live in [`../CLAUDE.md`](../CLAUDE.md).

## Intentional behaviors that look like bugs

### 1. `tracker.processed` counts attempts, not successes

**Where:** `com.data.oai.pipeline.GenericFacade#processOne` — the tracker is incremented inside the `finally` block.

```java
} finally {
    // Atomically increment at DB level — thread-safe, no JPA entity mutation from pool threads
    trackerService.incrementProcessed(tracker.getId());
    ...
}
```

**Why:** The tracker is the pipeline's forward-progress watermark. It must advance on every record we finished *attempting* so that:

- Restarts resume from the correct position rather than re-processing records that failed terminally.
- Operators can see pipeline progress even during long stretches of per-record failures (e.g. GROBID is down — attempts still accumulate, true successes do not).
- The tracker is never stuck because one PDF was corrupt.

**Implication:** `tracker.processed` is **not** a success count. The number of records actually persisted is lower and can only be computed by querying the persistence tables, not the tracker.

**Computing the true success rate for a batch:**

```sql
SELECT
  t.data_source,
  t.date_start,
  t.processed               AS attempts,
  COUNT(sr.id)              AS successes,
  ROUND(100.0 * COUNT(sr.id) / NULLIF(t.processed, 0), 2) AS success_pct
FROM oai_tracker t
LEFT JOIN source_record sr
       ON sr.data_source = t.data_source
      AND sr.datestamp BETWEEN t.date_start AND t.date_end
GROUP BY t.data_source, t.date_start, t.processed
ORDER BY t.date_start DESC;
```

**When to revisit:** If we ever add a monitoring SLO on true success rate, we should add a separate counter (e.g. `processed_success`) rather than change the semantics of `processed`.

---

### 2. Per-day harvest loads the full day into memory

**Where:** `com.data.oai.shared.AbstractOaiService#fetchAllRecords` — the template method that pages through OAI resumption tokens collects every record for the requested date window into a single in-memory `List<Record>` before returning.

**Why:** Safe at current scale. The harvest granularity is one day per `Tracker` row, and none of our current OAI sources have produced more than the low-tens-of-thousands of records per day:

- ArXiv: single-digit thousands/day peak.
- Zenodo: single-digit thousands/day peak.
- PubMed Central (OAI path): hundreds/day peak.

A `Record` is a lightweight DTO (a few strings plus small collections), so a ~20k/day ceiling stays well under the JVM's working set. Streaming through resumption tokens one record at a time would add complexity (cursor plumbing, partial-batch persistence, mid-batch restart semantics) for no measurable gain today.

**When to revisit — any of:**

- A source's daily volume crosses ~50k records. At that point a `List<Record>` starts pressuring young-gen GC.
- We introduce a source where metadata objects carry non-trivial payloads (e.g. full abstracts embedded in the OAI response rather than fetched on demand).
- We want to harvest wider windows (multi-week) in a single tracker row.

The refactor, when needed, is to replace `List<Record>` with a `Stream<Record>` (or `Iterator<Record>`) that pages through resumption tokens lazily, and push the dedup / persistence into the stream consumer.

---

### 3. Blank-title records are retained via fallback

**Where:** `isLikelyScholarlyText` in the OAI services. Records that reach the filter without a title are not rejected out of hand — they are accepted if the body/abstract look scholarly, and the title is later supplied from a GROBID-extracted title or left empty.

**Why:** Some upstream sources (notably Zenodo) publish records with empty or whitespace-only `<dc:title>` elements. Dropping those records would mean discarding otherwise-usable commercially-licensed scholarly content. Downstream the pipeline fills the title where possible:

- GROBID extracts a title from the PDF when available.
- The `PaperDocument#withFallbacks` helper substitutes a fallback title if GROBID also returns blank.
- Failing both, the `title` column stays `NULL` or empty — and that is tolerated by every downstream consumer we have today.

**Concrete evidence:** As of the Session 1 end-to-end audit (2026-04-17), 201 Zenodo rows had a blank `source_record.title` or an empty `record_document.title` after the full pipeline. None of them failed validation, and all had commercial-use-compatible licenses.

**Downstream contract:** Every consumer of `source_record` / `record_document` **must** tolerate a null or blank `title`. Specifically:

- Export serializers must emit `null` or `""` without failing (see `com.data.storage.S3ExportService`).
- Any future search indexer must not require `title` as a non-null facet.
- UI code that surfaces these records must fall back to `source_identifier` (or DOI) for a human-readable label.

**When to revisit:** If we add a consumer that genuinely cannot tolerate blank titles, we should reject title-less records at the OAI filter stage — not patch over it downstream with a synthesized placeholder.

---

### 4. License filter `rejectND=true` on all OAI sources

**Where:** `com.data.shared.license.LicenseFilter#isPermissiveLicense(license, rejectND, rejectSA)` is called with `rejectND=true, rejectSA=false` in:

- `com.data.oai.zenodo.ZenodoOaiService` (line 229).
- `com.data.oai.pubmed.PubmedOaiService` (line 288).

ArXiv does not use `isPermissiveLicense`; it uses the stricter `LicenseFilter#isAcceptableByUrlWhitelist`, which only accepts three exact URLs (CC-BY 4.0, CC-BY-SA 4.0, CC0 1.0). ArXiv is therefore implicitly ND-safe.

PMC S3 uses its own `com.data.pmcs3.pipeline.PmcS3LicenseFilter#isAcceptable`, which contains an explicit `if (normalized.contains("-nd")) return false;` guard.

**Why `rejectND` must stay true:** The product sells records that must be usable commercially **and** derivable. A license with `-ND` (NoDerivatives) forbids redistributing modified versions — which is incompatible with every downstream use case we support (embedding generation, section chunking, export transformations, LLM fine-tuning). `rejectSA` is left at `false` because ShareAlike is compatible with commercial use as long as derivatives inherit the same license, and our contracts can accommodate that.

**Accepted licenses — the full whitelist:**

| License | Accepted? | Notes |
|---|---|---|
| CC0 / Public Domain | Yes | All sources. |
| CC-BY (any version) | Yes | All sources. |
| CC-BY-SA (any version) | Yes | All sources. `rejectSA=false`. |
| CC-BY-ND | **No** | `rejectND=true`. |
| CC-BY-NC (any) | **No** | `-nc` rejected unconditionally. |
| CC-BY-NC-ND | **No** | Both `-nc` and `-nd` triggers. |
| MIT | Yes | OAI pattern-match path. |
| Apache-2.0 | Yes | OAI pattern-match path. |
| BSD-2-Clause, BSD-3-Clause | Yes | OAI pattern-match path. |
| ISC | Yes | OAI pattern-match path. |
| GPL / LGPL / AGPL | **No** | Incompatible with proprietary redistribution downstream. |

**When to revisit:** Only under explicit product sign-off. Loosening `rejectND` would require every downstream consumer to be audited for its handling of non-derivable content — and any export contract that promised "derivable" records to a customer would need renegotiation first.

See also: [`integrations.md`](./integrations.md) for source-specific license fields, and the regression tests in `src/test/java/com/data/shared/license/LicenseFilterTest.java` which lock the current whitelist/rejection behavior in place.
