package com.indira.opsconsole.controller;

import com.indira.opsconsole.controller.dto.*;
import com.indira.opsconsole.detection.CaseWorkflowService;
import com.indira.opsconsole.domain.entity.*;
import com.indira.opsconsole.domain.enums.*;
import com.indira.opsconsole.repository.*;
import com.indira.opsconsole.security.AuthHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GET  /api/cases                    — paginated, server-filtered by user scope + severity/state
 * GET  /api/cases/:id                — full case detail with evidence pane, notes, transitions
 * POST /api/cases/:id/notes          — add audit-stamped note (INVESTIGATOR, OPS_LEAD)
 * POST /api/cases/:id/transition     — state transition with optimistic lock (INVESTIGATOR, OPS_LEAD)
 *
 * SUPPORT role: client_id masked, raw_json hidden, narration hidden.
 * All scope filtering is enforced server-side, not just in buttons.
 */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final ExceptionCaseRepository  caseRepo;
    private final CaseEvidenceRepository   evidenceRepo;
    private final CaseNoteRepository       noteRepo;
    private final CaseTransitionRepository transitionRepo;
    private final RawRowRepository         rawRowRepo;
    private final CaseWorkflowService      workflowSvc;
    private final AuthHelper               authHelper;

    // ── GET /api/cases ────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<PageResponse<CaseSummaryResponse>> listCases(
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "50")    int size,
            @RequestParam(required = false)       String severity,
            @RequestParam(required = false)       String state,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {

        AppUser actor = authHelper.currentUser();
        List<String> scopedClientIds = scopedClients(actor);
        if (scopedClientIds.isEmpty()) {
            return ResponseEntity.ok(emptyPage(page, size));
        }

        // Parse sort
        String[] sortParts = sort.split(",");
        String   sortField = sortParts[0];
        Sort.Direction dir = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
            Sort.by(dir, sortField));

        CaseSeverity sevFilter   = severity != null ? parseSeverity(severity)   : null;
        CaseState    stateFilter = state    != null ? parseState(state)         : null;

        Page<ExceptionCase> resultPage;
        if (sevFilter != null && stateFilter != null) {
            resultPage = caseRepo.findByClientIdInAndSeverityAndState(
                scopedClientIds, sevFilter, stateFilter, pageable);
        } else if (sevFilter != null) {
            resultPage = caseRepo.findByClientIdInAndSeverity(
                scopedClientIds, sevFilter, pageable);
        } else if (stateFilter != null) {
            resultPage = caseRepo.findByClientIdInAndState(
                scopedClientIds, stateFilter, pageable);
        } else {
            resultPage = caseRepo.findByClientIdIn(scopedClientIds, pageable);
        }

        List<CaseSummaryResponse> items = resultPage.getContent().stream()
            .map(c -> toSummary(c, actor))
            .collect(Collectors.toList());

        return ResponseEntity.ok(PageResponse.<CaseSummaryResponse>builder()
            .items(items)
            .page(page)
            .pageSize(size)
            .totalItems(resultPage.getTotalElements())
            .totalPages(resultPage.getTotalPages())
            .hasNext(resultPage.hasNext())
            .hasPrevious(resultPage.hasPrevious())
            .build());
    }

    // ── GET /api/cases/:id ────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<CaseDetailResponse> getCase(@PathVariable String id) {
        AppUser actor = authHelper.currentUser();
        List<String> scopedClientIds = scopedClients(actor);

        ExceptionCase c = caseRepo.findByIdAndClientIdIn(id, scopedClientIds)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Case not found or access denied: " + id));

        boolean isSupport = actor.getRole() == UserRole.SUPPORT;

        // Evidence pane
        List<CaseEvidence> evidenceList = evidenceRepo.findByExceptionCaseId(id);
        List<CaseDetailResponse.EvidenceItem> evidenceItems = evidenceList.stream()
            .map(ev -> {
                String rawJson = null;
                if (!isSupport && ev.getRawRow() != null) {
                    rawJson = ev.getRawRow().getRawJson();
                }
                return CaseDetailResponse.EvidenceItem.builder()
                    .evidenceId(ev.getId())
                    .evidenceRole(ev.getEvidenceRole())
                    .sourceFileId(ev.getSourceFile().getId())
                    .streamName(ev.getSourceFile().getStreamName())
                    .cutAt(ev.getSourceFile().getCutAt())
                    .receivedAt(ev.getSourceFile().getReceivedAt())
                    .filename(ev.getSourceFile().getFilename())
                    .sha256Hash(ev.getSourceFile().getSha256Hash())
                    .mappingVersion(ev.getMappingVersion())
                    .rawRowId(ev.getRawRow() != null ? ev.getRawRow().getId() : null)
                    .rawJson(rawJson)
                    .build();
            })
            .collect(Collectors.toList());

        // Notes
        List<CaseDetailResponse.NoteItem> noteItems =
            noteRepo.findByExceptionCaseIdOrderByCreatedAtAsc(id).stream()
                .map(n -> CaseDetailResponse.NoteItem.builder()
                    .id(n.getId())
                    .authorUsername(n.getAuthor().getUsername())
                    .body(n.getBody())
                    .createdAt(n.getCreatedAt())
                    .evidenceVersionSnapshot(n.getEvidenceVersionSnapshot())
                    .build())
                .collect(Collectors.toList());

        // Transitions
        List<CaseDetailResponse.TransitionItem> transItems =
            transitionRepo.findByExceptionCaseIdOrderByChangedAtAsc(id).stream()
                .map(t -> CaseDetailResponse.TransitionItem.builder()
                    .id(t.getId())
                    .fromState(t.getFromState())
                    .toState(t.getToState())
                    .reason(t.getReason())
                    .byUsername(t.getUser().getUsername())
                    .evidenceVersion(t.getEvidenceVersion())
                    .changedAt(t.getChangedAt())
                    .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(CaseDetailResponse.builder()
            .id(c.getId())
            .caseType(c.getCaseType())
            .severity(c.getSeverity())
            .state(c.getState())
            .clientId(authHelper.maskClientId(c.getClientId(), actor))
            .isin(c.getIsin())
            .cutAt(c.getCutAt())
            .quantityDelta(c.getQuantityDelta())
            .amountDeltaPaise(isSupport ? null : c.getAmountDeltaPaise())
            .evidenceState(c.getEvidenceState())
            .description(c.getDescription())
            .version(c.getVersion())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .evidence(evidenceItems)
            .notes(noteItems)
            .transitions(transItems)
            .build());
    }

    // ── POST /api/cases/:id/notes ─────────────────────────────────────────────

    @PostMapping("/{id}/notes")
    public ResponseEntity<CaseDetailResponse.NoteItem> addNote(
            @PathVariable String id,
            @Valid @RequestBody NoteRequest body) {

        AppUser actor = authHelper.currentUser();
        CaseNote note = workflowSvc.addNote(id, body.getBody(), actor);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            CaseDetailResponse.NoteItem.builder()
                .id(note.getId())
                .authorUsername(actor.getUsername())
                .body(note.getBody())
                .createdAt(note.getCreatedAt())
                .evidenceVersionSnapshot(note.getEvidenceVersionSnapshot())
                .build());
    }

    // ── POST /api/cases/:id/transition ────────────────────────────────────────

    @PostMapping("/{id}/transition")
    public ResponseEntity<CaseSummaryResponse> transition(
            @PathVariable String id,
            @Valid @RequestBody TransitionRequest body) {

        AppUser actor = authHelper.currentUser();
        CaseState targetState = parseState(body.getTargetState());
        ExceptionCase updated = workflowSvc.transition(
            id, targetState, body.getReason(), body.getExpectedVersion(), actor);

        return ResponseEntity.ok(toSummary(updated, actor));
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    @Data
    public static class NoteRequest {
        @NotBlank(message = "Note body must not be blank")
        private String body;
    }

    @Data
    public static class TransitionRequest {
        @NotBlank(message = "targetState is required")
        private String targetState;

        @NotBlank(message = "reason is required")
        private String reason;

        @NotNull(message = "expectedVersion is required for optimistic locking")
        private Integer expectedVersion;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> scopedClients(AppUser actor) {
        return switch (actor.getRole()) {
            case OPS_LEAD, AUDITOR -> caseRepo.findDistinctClientIds();
            default -> new ArrayList<>(actor.getAccessibleClientIds());
        };
    }

    private CaseSummaryResponse toSummary(ExceptionCase c, AppUser actor) {
        boolean isSupport = actor.getRole() == UserRole.SUPPORT;
        int noteCount = noteRepo.findByExceptionCaseIdOrderByCreatedAtAsc(c.getId()).size();
        return CaseSummaryResponse.builder()
            .id(c.getId())
            .caseType(c.getCaseType())
            .severity(c.getSeverity())
            .state(c.getState())
            .clientId(authHelper.maskClientId(c.getClientId(), actor))
            .isin(c.getIsin())
            .cutAt(c.getCutAt())
            .quantityDelta(c.getQuantityDelta())
            .amountDeltaPaise(isSupport ? null : c.getAmountDeltaPaise())
            .evidenceState(c.getEvidenceState())
            .description(c.getDescription())
            .version(c.getVersion())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .noteCount(noteCount)
            .build();
    }

    private PageResponse<CaseSummaryResponse> emptyPage(int page, int size) {
        return PageResponse.<CaseSummaryResponse>builder()
            .items(List.of()).page(page).pageSize(size)
            .totalItems(0).totalPages(0)
            .hasNext(false).hasPrevious(false)
            .build();
    }

    private CaseSeverity parseSeverity(String s) {
        try { return CaseSeverity.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity: " + s);
        }
    }

    private CaseState parseState(String s) {
        try { return CaseState.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state: " + s);
        }
    }
}
