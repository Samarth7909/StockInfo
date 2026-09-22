package com.indira.opsconsole.detection;

import com.indira.opsconsole.domain.entity.*;
import com.indira.opsconsole.domain.enums.*;
import com.indira.opsconsole.repository.CaseEvidenceRepository;
import com.indira.opsconsole.repository.ExceptionCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Creates or reopens ExceptionCase records and attaches CaseEvidence links.
 *
 * Reopen logic (domain rule 6):
 *   If a RESOLVED case for the same (clientId, isin, cutAt, caseType) exists
 *   and the new detection would produce a different result, reopen it.
 *   A note is NOT modified — notes survive file corrections.
 */
@Component
@RequiredArgsConstructor
public class CaseFactory {

    private final ExceptionCaseRepository caseRepo;
    private final CaseEvidenceRepository  evidenceRepo;

    /**
     * Create a new case or reopen an existing RESOLVED case.
     * Returns the case and a flag indicating whether it was newly created.
     */
    public CaseUpsertResult upsert(
            CaseType caseType,
            CaseSeverity severity,
            EvidenceState evidenceState,
            String clientId,
            String isin,
            Instant cutAt,
            Long quantityDelta,
            Long amountDeltaPaise,
            String description,
            List<EvidenceLink> evidenceLinks) {

        // Check for an existing non-resolved case for this key
        List<ExceptionCase> existing = caseRepo.findByClientIdAndIsinAndCutAtAndCaseType(
            clientId, isin, cutAt, caseType);

        // Prefer OPEN/INVESTIGATING/REOPENED over RESOLVED
        ExceptionCase existingCase = existing.stream()
            .filter(c -> c.getState() != CaseState.RESOLVED)
            .findFirst()
            .orElse(existing.stream()
                .filter(c -> c.getState() == CaseState.RESOLVED)
                .findFirst()
                .orElse(null));

        boolean isNew = false;
        boolean isReopened = false;

        ExceptionCase exceptionCase;
        if (existingCase == null) {
            exceptionCase = ExceptionCase.builder()
                .caseType(caseType)
                .severity(severity)
                .state(CaseState.OPEN)
                .clientId(clientId)
                .isin(isin)
                .cutAt(cutAt)
                .quantityDelta(quantityDelta)
                .amountDeltaPaise(amountDeltaPaise)
                .evidenceState(evidenceState)
                .description(description)
                .build();
            exceptionCase = caseRepo.save(exceptionCase);
            isNew = true;
        } else if (existingCase.getState() == CaseState.RESOLVED) {
            // Reopen — evidence has changed
            existingCase.setState(CaseState.REOPENED);
            existingCase.setSeverity(severity);
            existingCase.setEvidenceState(evidenceState);
            existingCase.setQuantityDelta(quantityDelta);
            existingCase.setAmountDeltaPaise(amountDeltaPaise);
            existingCase.setDescription(description);
            exceptionCase = caseRepo.save(existingCase);
            isReopened = true;
        } else {
            // Update in-place (keep notes/transitions intact)
            existingCase.setSeverity(severity);
            existingCase.setEvidenceState(evidenceState);
            existingCase.setQuantityDelta(quantityDelta);
            existingCase.setAmountDeltaPaise(amountDeltaPaise);
            existingCase.setDescription(description);
            exceptionCase = caseRepo.save(existingCase);
        }

        // Attach evidence links
        for (EvidenceLink link : evidenceLinks) {
            CaseEvidence ev = CaseEvidence.builder()
                .exceptionCase(exceptionCase)
                .sourceFile(link.sourceFile())
                .rawRow(link.rawRow())
                .mappingVersion(link.mappingVersion())
                .evidenceRole(link.role())
                .build();
            evidenceRepo.save(ev);
        }

        return new CaseUpsertResult(exceptionCase, isNew, isReopened);
    }

    public record CaseUpsertResult(ExceptionCase exceptionCase, boolean created, boolean reopened) {}

    public record EvidenceLink(SourceFile sourceFile, RawRow rawRow, String mappingVersion, String role) {
        public static EvidenceLink of(SourceFile sf, RawRow rr, String role) {
            return new EvidenceLink(sf, rr, "v1", role);
        }
    }
}
