package com.indira.opsconsole.ingest;

import com.indira.opsconsole.domain.enums.SourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Result returned to the caller after an ingest attempt.
 */
@Getter
@Builder
public class IngestResult {
    private final String       sourceFileId;
    private final boolean      idempotent;     // true = same hash already existed, no-op
    private final SourceStatus status;
    private final int          rowCount;
    private final int          errorCount;
    private final int          casesCreated;
    private final int          casesReopened;
    private final List<String> errorMessages;  // row-level error details
    private final String       errorDetail;    // file-level error if status=ERROR
}
