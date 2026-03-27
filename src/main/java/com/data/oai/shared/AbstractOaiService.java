package com.data.oai.shared;

import com.data.oai.pipeline.DataSource;
import com.data.oai.pipeline.OaiSourceHandler;
import com.data.oai.shared.dto.OaiPage;
import com.data.oai.shared.dto.PdfContent;
import com.data.oai.shared.dto.Record;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Template for OAI-PMH source services. Captures the shared pagination loop
 * and implements {@link OaiSourceHandler}, eliminating the need for separate
 * handler adapter classes.
 *
 * <p>Subclasses provide:
 * <ul>
 *   <li>{@link #callListRecords} — delegates to the source-specific client</li>
 *   <li>{@link #parseResponse} — source-specific XML parsing</li>
 *   <li>{@link #getPdf} — source-specific PDF resolution and download</li>
 * </ul>
 *
 * <p>Adding a new OAI source requires only a client class and a service
 * extending this base. No handler class, no registry changes.</p>
 */
@Slf4j
public abstract class AbstractOaiService implements OaiSourceHandler {

    // ── OaiSourceHandler implementation ──────────────────────────────

    @Override
    public List<Record> fetchMetadata(LocalDate startInclusive, LocalDate endInclusive) {
        return fetchAllRecords(startInclusive.toString(), endInclusive.toString());
    }

    @Override
    public PdfContent fetchPdfAndEnrich(Record record) {
        return getPdf(record.getSourceId());
    }

    // ── Template method: shared pagination loop ──────────────────────

    /**
     * Fetches all records for the given date range, automatically following
     * OAI-PMH resumptionTokens across pages.
     */
    public List<Record> fetchAllRecords(String from, String until) {
        List<Record> collected = new ArrayList<>();
        String token = null;

        do {
            byte[] body = callListRecords(from, until, token);

            if (body == null) {
                log.info("[{}] No records available for period {} to {}", sourceName(), from, until);
                break;
            }

            OaiPage page = parseResponse(body);
            collected.addAll(page.records());
            token = page.resumptionToken();

            if (token != null && !token.isBlank()) {
                sleep(paginationDelayMs());
            }
        } while (token != null && !token.isBlank());

        log.info("[{}] Collected {} records for period {} to {}", sourceName(), collected.size(), from, until);
        return collected;
    }

    // ── Hooks for source-specific behavior ───────────────────────────

    /** Source name for logging (e.g., "ArXiv", "Zenodo", "PMC"). */
    protected abstract String sourceName();

    /** Milliseconds to wait between pagination calls. */
    protected abstract long paginationDelayMs();

    /**
     * Calls the OAI-PMH client's listRecords endpoint.
     * May return {@code null} if the source signals "no records" (e.g., PubMed 404).
     */
    protected abstract byte[] callListRecords(String from, String until, String token);

    /** Parses the source-specific XML response into an {@link OaiPage}. */
    protected abstract OaiPage parseResponse(byte[] xmlBytes);

    /** Resolves and downloads the PDF for a given source-specific ID. */
    public abstract PdfContent getPdf(String sourceId);

    // ── Utility ──────────────────────────────────────────────────────

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
