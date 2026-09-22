package com.indira.opsconsole.controller.dto;

import com.indira.opsconsole.domain.enums.*;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Lean case record returned in paginated list — no raw rows, no notes. */
@Getter @Builder
public class CaseSummaryResponse {
    private String        id;
    private CaseType      caseType;
    private CaseSeverity  severity;
    private CaseState     state;
    private String        clientId;       // masked for SUPPORT role
    private String        isin;
    private Instant       cutAt;
    private Long          quantityDelta;
    private Long          amountDeltaPaise;
    private EvidenceState evidenceState;
    private String        description;
    private int           version;
    private Instant       createdAt;
    private Instant       updatedAt;
    private int           noteCount;
}
