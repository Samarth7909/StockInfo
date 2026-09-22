package com.indira.opsconsole.detection;

import com.indira.opsconsole.domain.entity.*;
import com.indira.opsconsole.domain.enums.*;
import com.indira.opsconsole.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs all detection rules after a file is ingested.
 *
 * Rules (from domain spec):
 * 1. HOLDING_MISMATCH  — internal settled qty ≠ DP settled qty at same (client, ISIN, position_type, cut)
 * 2. PENDING_DP        — DP rows with movement_state=PENDING
 * 3. CASH_RECONCILIATION — duplicate UTR reference in bank entries → CONFLICTING_EVIDENCE
 * 4. MISSING_SOURCE    — internal holding has no matching DP row for the same cut
 *
 * All joins are on ISIN only. Display symbol is never a join key.
 * Missing source = UNKNOWN, never zero.
 * Pending evidence can inform explanation but cannot erase a mismatch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseDetectionService {

    private final NormalizedHoldingRepository   holdingRepo;
    private final DpPendingMovementRepository   pendingRepo;
    private final CashEventRepository           cashEventRepo;
    private final BankEntryRepository           bankEntryRepo;
    private final ExceptionCaseRepository       caseRepo;
    private final CaseFactory                   caseFactory;

    @Transactional
    public DetectionResult detectForCut(Instant cutAt, SourceFile triggeringFile) {
        log.info("Running case detection for cut={} triggered by stream={}",
            cutAt, triggeringFile.getStreamName());

        int created  = 0;
        int reopened = 0;
        int matched  = 0;

        // Run each rule; accumulate counts
        var h = detectHoldingMismatches(cutAt, triggeringFile);
        created  += h[0]; reopened += h[1]; matched += h[2];

        var p = detectPendingDp(cutAt, triggeringFile);
        created  += p[0]; reopened += p[1];

        var c = detectCashConflicts(triggeringFile);
        created  += c[0]; reopened += c[1];

        var m = detectMissingSource(cutAt, triggeringFile);
        created  += m[0]; reopened += m[1];

        log.info("Detection complete: created={} reopened={} matched={}", created, reopened, matched);
        return DetectionResult.builder()
            .casesCreated(created).casesReopened(reopened).casesMatched(matched).build();
    }

    // ── Rule 1: Holding Mismatch ───────────────────────────────────────────────

    private int[] detectHoldingMismatches(Instant cutAt, SourceFile triggeringFile) {
        int created = 0, reopened = 0, matched = 0;

        List<NormalizedHolding> internalRows =
            holdingRepo.findByCutAtAndDataSource(cutAt, "INTERNAL");

        for (NormalizedHolding internal : internalRows) {
            List<NormalizedHolding> dpRows =
                holdingRepo.findByClientIdAndIsinAndPositionTypeAndCutAtAndDataSource(
                    internal.getClientId(), internal.getIsin(),
                    internal.getPositionType(), cutAt, "DP");

            if (dpRows.isEmpty()) {
                // No DP row for this holding → handled by MISSING_SOURCE rule
                continue;
            }

            // Use the first matching DP settled row (there should be exactly one settled per key)
            NormalizedHolding dp = dpRows.get(0);
            long delta = internal.getSettledQty() - dp.getSettledQty();

            if (delta == 0) {
                matched++;
                // Mark any existing open case as having no current mismatch
                resolveIfMatchedAndOpen(internal.getClientId(), internal.getIsin(), cutAt,
                    CaseType.HOLDING_MISMATCH);
                continue;
            }

            // Nonzero delta → HIGH case regardless of pending DP movements
            String description = String.format(
                "Settled holding mismatch: internal=%d DP=%d delta=%d for client=%s isin=%s cut=%s",
                internal.getSettledQty(), dp.getSettledQty(), delta,
                internal.getClientId(), internal.getIsin(), cutAt);

            List<CaseFactory.EvidenceLink> links = List.of(
                CaseFactory.EvidenceLink.of(internal.getSourceFile(), internal.getRawRow(), "INTERNAL_HOLDING"),
                CaseFactory.EvidenceLink.of(dp.getSourceFile(), dp.getRawRow(), "DP_HOLDING")
            );

            var result = caseFactory.upsert(
                CaseType.HOLDING_MISMATCH, CaseSeverity.HIGH, EvidenceState.UNMATCHED_IDENTITY,
                internal.getClientId(), internal.getIsin(), cutAt,
                delta, null, description, links);

            if (result.created())  created++;
            if (result.reopened()) reopened++;
        }

        return new int[]{created, reopened, matched};
    }

    // ── Rule 2: Pending DP Movement ──────────────────────────────────────────

    private int[] detectPendingDp(Instant cutAt, SourceFile triggeringFile) {
        int created = 0, reopened = 0;

        // Fetch pending movements for this cut via indexed query (not a full table scan)
        List<DpPendingMovement> pending = pendingRepo.findByCutAt(cutAt);

        for (DpPendingMovement pm : pending) {
            String description = String.format(
                "DP pending movement: qty=%d client=%s isin=%s cut=%s. " +
                "Pending evidence may inform explanation but cannot erase a settled mismatch.",
                pm.getPendingQty(), pm.getClientId(), pm.getIsin(), cutAt);

            List<CaseFactory.EvidenceLink> links = List.of(
                CaseFactory.EvidenceLink.of(pm.getSourceFile(), pm.getRawRow(), "DP_HOLDING")
            );

            var result = caseFactory.upsert(
                CaseType.PENDING_DP, CaseSeverity.MEDIUM, EvidenceState.UNKNOWN,
                pm.getClientId(), pm.getIsin(), cutAt,
                pm.getPendingQty(), null, description, links);

            if (result.created())  created++;
            if (result.reopened()) reopened++;
        }

        return new int[]{created, reopened};
    }

    // ── Rule 3: Cash / Bank Conflicts ─────────────────────────────────────────

    private int[] detectCashConflicts(SourceFile triggeringFile) {
        int created = 0, reopened = 0;

        // Only run if the triggering file is BANK_CONFIRMATION or CASH_LEDGER
        if (!"BANK_CONFIRMATION".equals(triggeringFile.getStreamName()) &&
            !"CASH_LEDGER".equals(triggeringFile.getStreamName())) {
            return new int[]{0, 0};
        }

        // Find all UTR references with duplicates
        List<BankEntry> allEntries = bankEntryRepo.findAll();
        Map<String, List<BankEntry>> byUtr = allEntries.stream()
            .filter(e -> e.getUtrReference() != null)
            .collect(Collectors.groupingBy(BankEntry::getUtrReference));

        for (Map.Entry<String, List<BankEntry>> entry : byUtr.entrySet()) {
            if (entry.getValue().size() < 2) continue;

            List<BankEntry> dupes = entry.getValue();
            BankEntry first = dupes.get(0);
            String clientId = first.getClientId() != null ? first.getClientId() : "UNKNOWN";

            String description = String.format(
                "Duplicate UTR reference '%s' appears %d times in bank feed. " +
                "Cannot auto-match — requires human verification of client, direction, amount, and timing.",
                entry.getKey(), dupes.size());

            List<CaseFactory.EvidenceLink> links = dupes.stream()
                .map(be -> CaseFactory.EvidenceLink.of(be.getSourceFile(), be.getRawRow(), "BANK_ENTRY"))
                .collect(Collectors.toList());

            // Use the max value_date as the cut proxy
            Instant cutProxy = dupes.stream()
                .filter(be -> be.getValueDate() != null)
                .map(be -> be.getValueDate().atStartOfDay()
                    .toInstant(java.time.ZoneOffset.UTC))
                .max(Instant::compareTo)
                .orElse(Instant.now());

            var result = caseFactory.upsert(
                CaseType.CASH_RECONCILIATION, CaseSeverity.MEDIUM, EvidenceState.CONFLICTING_EVIDENCE,
                clientId, null, cutProxy,
                null, null, description, links);

            if (result.created())  created++;
            if (result.reopened()) reopened++;
        }

        return new int[]{created, reopened};
    }

    // ── Rule 4: Missing Source ─────────────────────────────────────────────────

    private int[] detectMissingSource(Instant cutAt, SourceFile triggeringFile) {
        int created = 0, reopened = 0;

        // Only meaningful when INTERNAL holdings are present for the cut
        List<NormalizedHolding> internalRows =
            holdingRepo.findByCutAtAndDataSource(cutAt, "INTERNAL");

        for (NormalizedHolding internal : internalRows) {
            List<NormalizedHolding> dpRows =
                holdingRepo.findByClientIdAndIsinAndPositionTypeAndCutAtAndDataSource(
                    internal.getClientId(), internal.getIsin(),
                    internal.getPositionType(), cutAt, "DP");

            if (!dpRows.isEmpty()) continue;  // DP row exists — handled by mismatch rule

            String description = String.format(
                "No DP row for client=%s isin=%s positionType=%s cut=%s. " +
                "Source is absent — this is UNKNOWN, not zero. Cannot label as MATCHED.",
                internal.getClientId(), internal.getIsin(),
                internal.getPositionType(), cutAt);

            List<CaseFactory.EvidenceLink> links = List.of(
                CaseFactory.EvidenceLink.of(internal.getSourceFile(), internal.getRawRow(), "INTERNAL_HOLDING")
            );

            var result = caseFactory.upsert(
                CaseType.MISSING_SOURCE, CaseSeverity.MEDIUM, EvidenceState.MISSING_SOURCE,
                internal.getClientId(), internal.getIsin(), cutAt,
                null, null, description, links);

            if (result.created())  created++;
            if (result.reopened()) reopened++;
        }

        return new int[]{created, reopened};
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /**
     * If an existing OPEN/INVESTIGATING case for this key now shows zero delta,
     * update its evidenceState to MATCHED but do NOT auto-resolve it —
     * only an ops_lead can resolve a financial discrepancy (domain rule).
     */
    private void resolveIfMatchedAndOpen(String clientId, String isin, Instant cutAt, CaseType type) {
        List<ExceptionCase> openCases = caseRepo.findByClientIdAndIsinAndCutAtAndCaseType(
            clientId, isin, cutAt, type);
        for (ExceptionCase c : openCases) {
            if (c.getState() == CaseState.OPEN || c.getState() == CaseState.INVESTIGATING) {
                c.setEvidenceState(EvidenceState.MATCHED);
                c.setQuantityDelta(0L);
                caseRepo.save(c);
            }
        }
    }
}
