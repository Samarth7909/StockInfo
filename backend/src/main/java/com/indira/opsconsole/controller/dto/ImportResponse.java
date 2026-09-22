package com.indira.opsconsole.controller.dto;

import com.indira.opsconsole.domain.enums.SourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter @Builder
public class ImportResponse {
    private String       id;
    private String       streamName;
    private Instant      cutAt;
    private Instant      receivedAt;
    private String       filename;
    private String       sha256Hash;
    private String       schemaVersion;
    private String       mappingVersion;
    private int          rowCount;
    private int          errorCount;
    private SourceStatus status;
    private String       errorDetail;
    private boolean      idempotent;
    private int          casesCreated;
    private int          casesReopened;
    private List<String> errorMessages;
    private String       importedBy;
}
