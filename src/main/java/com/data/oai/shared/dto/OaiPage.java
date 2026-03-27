package com.data.oai.shared.dto;

import java.util.List;

/**
 * A single page of OAI-PMH ListRecords results.
 * Shared across ArXiv, Zenodo, and PubMed parsers.
 *
 * @param records          parsed records from this page
 * @param resumptionToken  token for the next page, or null if this is the last page
 */
public record OaiPage(List<Record> records, String resumptionToken) {}
