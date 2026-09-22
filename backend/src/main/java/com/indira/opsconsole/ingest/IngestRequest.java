package com.indira.opsconsole.ingest;

import lombok.Builder;
import lombok.Getter;

/**
 * Input to IngestService.ingest().
 * Contains the raw bytes, stream name, original filename, and the requesting user ID.
 */
@Getter
@Builder
public class IngestRequest {
    private final String streamName;
    private final byte[] content;
    private final String filename;
    private final String importedByUserId;
}
