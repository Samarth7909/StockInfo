package com.indira.opsconsole.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indira.opsconsole.domain.entity.*;
import com.indira.opsconsole.domain.enums.*;
import com.indira.opsconsole.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles case state transitions and note creation with full audit logging.
 *
 * Transition rules:
 * - Only OPS_LEAD may transition to RESOLVED (domain rule: only ops lead resolves financial discrepancy).
 * - INVESTIGATOR and OPS_LEAD may transition to INVESTIGATING / NEEDS_SOURCE / REOPENED.
 * - Optimistic locking: caller must supply expected version; mismatch → 409 Conflict.
 * - A note may say "pending DP movement might explain delta" but must not rewrite source data.
 * - Only an investigator with access to that client may add a note.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseWorkflowService {

    private final ExceptionCaseRepository   caseRepo;
    private final CaseNoteRepository        noteRepo;
    private final CaseTransitionRepository  transitionRepo;
    private final CaseEvidenceRepository    evidenceRepo;
    private final AppUserRepository         userRepo;
    private final ObjectMapper              objectMapper;

    // ── Add Note ──────────────────────────────────────────────────────────────

    @Transactional
    public CaseNote addNote(String caseId, String body, AppUser author) {
        ExceptionCase c = caseRepo.findById(caseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId));

        // SUPPORT and AUDITOR may not add notes
        if (author.getRole() == UserRole.SUPPORT || author.getRole() == UserRole.AUDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Role " + author.getRole() + " may not add notes.");
        }

        // Scope check — investigator can only note on accessible clients
        if (author.getRole() == UserRole.INVESTIGATOR &&
            !author.getAccessibleClientIds().contains(c.getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Investigator does not have access to client: " + c.getClientId());
        }

        // Snapshot the current evidence file IDs at time of note
        String snapshot = buildEvidenceSnapshot(caseId);

        CaseNote note = CaseNote.builder()
            .exceptionCase(c)
            .author(author)
            .body(body)
            .evidenceVersionSnapshot(snapshot)
            .build();
        return noteRepo.save(note);
    }

    // ── Transition ────────────────────────────────────────────────────────────

    @Transactional
    public ExceptionCase transition(String caseId, CaseState targetState,
                                    String reason, int expectedVersion, AppUser actor) {
        ExceptionCase c = caseRepo.findById(caseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId));

        // Optimistic lock check
        if (c.getVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                String.format("Version conflict: expected %d but current version is %d. " +
                    "Another user may have modified this case.", expectedVersion, c.getVersion()));
        }

        // Role-based transition rules
        validateTransition(c.getState(), targetState, actor);

        // Scope check
        if (!actor.getAccessibleClientIds().contains(c.getClientId()) &&
            actor.getRole() != UserRole.AUDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "User does not have access to client: " + c.getClientId());
        }

        CaseState fromState = c.getState();
        c.setState(targetState);
        ExceptionCase saved = caseRepo.save(c);

        // Audit trail
        CaseTransition audit = CaseTransition.builder()
            .exceptionCase(saved)
            .fromState(fromState)
            .toState(targetState)
            .reason(reason)
            .user(actor)
            .evidenceVersion(expectedVersion)
            .build();
        transitionRepo.save(audit);

        log.info("Case {} transitioned {} → {} by {} (reason: {})",
            caseId, fromState, targetState, actor.getUsername(), reason);
        return saved;
    }

    // ── Allowed transitions ───────────────────────────────────────────────────

    private static final Map<CaseState, List<CaseState>> ALLOWED_TRANSITIONS = Map.of(
        CaseState.OPEN,          List.of(CaseState.INVESTIGATING, CaseState.NEEDS_SOURCE, CaseState.RESOLVED),
        CaseState.INVESTIGATING, List.of(CaseState.NEEDS_SOURCE, CaseState.RESOLVED, CaseState.OPEN),
        CaseState.NEEDS_SOURCE,  List.of(CaseState.INVESTIGATING, CaseState.RESOLVED, CaseState.OPEN),
        CaseState.RESOLVED,      List.of(CaseState.REOPENED),
        CaseState.REOPENED,      List.of(CaseState.INVESTIGATING, CaseState.NEEDS_SOURCE, CaseState.RESOLVED)
    );

    private void validateTransition(CaseState from, CaseState to, AppUser actor) {
        List<CaseState> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, List.of());
        if (!allowed.contains(to)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                String.format("Transition %s → %s is not allowed.", from, to));
        }

        // Only OPS_LEAD may resolve a financial discrepancy
        if (to == CaseState.RESOLVED && actor.getRole() != UserRole.OPS_LEAD) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only OPS_LEAD may resolve a case.");
        }

        // AUDITOR and SUPPORT cannot transition
        if (actor.getRole() == UserRole.AUDITOR || actor.getRole() == UserRole.SUPPORT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Role " + actor.getRole() + " may not change case state.");
        }
    }

    // ── Evidence snapshot ─────────────────────────────────────────────────────

    private String buildEvidenceSnapshot(String caseId) {
        List<CaseEvidence> evidence = evidenceRepo.findByExceptionCaseId(caseId);
        List<Map<String, String>> snapshot = evidence.stream()
            .map(ev -> Map.of(
                "sourceFileId",   ev.getSourceFile().getId(),
                "streamName",     ev.getSourceFile().getStreamName(),
                "sha256",         ev.getSourceFile().getSha256Hash(),
                "mappingVersion", ev.getMappingVersion(),
                "role",           ev.getEvidenceRole()
            ))
            .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "[]";
        }
    }
}
