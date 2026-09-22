package com.indira.opsconsole.controller.dto;

import com.indira.opsconsole.domain.enums.*;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** Full case record including evidence pane, notes, and transition audit. */
@Getter @Builder
public class CaseDetailResponse {
    private String        id;
    private CaseType      caseType;
    private CaseSeverity  severity;
    private CaseState     state;
    private String        clientId;
    private String        isin;
    private Instant       cutAt;
    private Long          quantityDelta;
    private Long          amountDeltaPaise;
    private EvidenceState evidenceState;
    private String        description;
    private int           version;
    private Instant       createdAt;
    private Instant       updatedAt;

    /** Evidence pane: source row + version references. */
    private List<EvidenceItem> evidence;

    /** Audit trail of notes (chronological). */
    private List<NoteItem> notes;

    /** State transition history. */
    private List<TransitionItem> transitions;

    @Getter @Builder
    public static class EvidenceItem {
        private String  evidenceId;
        private String  evidenceRole;
        private String  sourceFileId;
        private String  streamName;
        private Instant cutAt;
        private Instant receivedAt;
        private String  filename;
        private String  sha256Hash;
        private String  mappingVersion;
        private String  rawRowId;
        private String  rawJson;          // null for SUPPORT role
    }

    @Getter @Builder
    public static class NoteItem {
        private String  id;
        private String  authorUsername;
        private String  body;
        private Instant createdAt;
        private String  evidenceVersionSnapshot;
    }

    @Getter @Builder
    public static class TransitionItem {
        private String    id;
        private CaseState fromState;
        private CaseState toState;
        private String    reason;
        private String    byUsername;
        private int       evidenceVersion;
        private Instant   changedAt;
    }
}
