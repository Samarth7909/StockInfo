package com.indira.opsconsole.ingest.adapter;

/**
 * Shared interface for every stream parser.
 * parse() reads raw bytes and returns rows + errors.
 * The calling IngestService handles persistence; adapters never touch the database.
 */
public interface StreamAdapter {

    /** Canonical stream name (e.g. "HOLDINGS", "DP_EXTRACT"). */
    String streamName();

    /**
     * Parse raw file bytes into rows and quarantine errors.
     *
     * @param raw      full file content
     * @param filename original filename (used for logging and schema detection)
     * @return ParseResult with rows, errors, and detected cut timestamp
     */
    ParseResult parse(byte[] raw, String filename);
}
