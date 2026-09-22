package com.indira.opsconsole.ingest.adapter;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result returned by every StreamAdapter after parsing a raw file.
 * rawRows are the original rows as LinkedHashMap<String,String> (header→value).
 * errors are row-level quarantine messages (row index → message).
 * detectedCut is the report cut timestamp extracted from file content; null if not determinable.
 */
@Getter
@Builder
public class ParseResult {
    private final List<Map<String, String>> rawRows;
    private final List<RowError>            errors;
    private final Instant                   detectedCut;
    private final String                    schemaVersion;

    @Getter
    @Builder
    public static class RowError {
        private final int    rowIndex;
        private final String field;
        private final String message;
        private final String rawValue;
    }
}
