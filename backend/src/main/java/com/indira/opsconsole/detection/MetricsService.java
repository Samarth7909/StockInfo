package com.indira.opsconsole.detection;

import com.indira.opsconsole.domain.enums.CaseSeverity;
import com.indira.opsconsole.domain.enums.SourceStatus;
import com.indira.opsconsole.repository.ExceptionCaseRepository;
import com.indira.opsconsole.repository.SourceFileRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes operational metrics for GET /metrics.
 * All values are derived from DB state at query time — no external calls.
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final SourceFileRepository     sourceFileRepo;
    private final ExceptionCaseRepository  caseRepo;

    public MetricsSnapshot snapshot(List<String> visibleClientIds) {
        // Source freshness: latest received_at per stream
        Map<String, Instant> streamFreshness = new LinkedHashMap<>();
        List<String> knownStreams = List.of(
            "HOLDINGS", "DP_EXTRACT", "CASH_LEDGER", "BANK_CONFIRMATION", "EXCHANGE_REF");
        for (String stream : knownStreams) {
            sourceFileRepo.findByStreamNameOrderByReceivedAtDesc(stream).stream()
                .findFirst()
                .ifPresentOrElse(
                    sf -> streamFreshness.put(stream, sf.getReceivedAt()),
                    ()  -> streamFreshness.put(stream, null));
        }

        // Pending imports
        long pendingImports = sourceFileRepo.findByStatus(SourceStatus.PENDING).size()
            + sourceFileRepo.findByStatus(SourceStatus.PROCESSING).size();

        // Error imports
        long errorImports = sourceFileRepo.findByStatus(SourceStatus.ERROR).size();

        // Open cases by severity (scoped to visible clients)
        Map<String, Long> openBySeverity = new LinkedHashMap<>();
        openBySeverity.put("CRITICAL", 0L);
        openBySeverity.put("HIGH",     0L);
        openBySeverity.put("MEDIUM",   0L);
        openBySeverity.put("LOW",      0L);

        if (!visibleClientIds.isEmpty()) {
            List<Object[]> rows = caseRepo.countBySeverityForClients(
                visibleClientIds, com.indira.opsconsole.domain.enums.CaseState.RESOLVED);
            for (Object[] row : rows) {
                CaseSeverity sev = (CaseSeverity) row[0];
                Long count = (Long) row[1];
                openBySeverity.put(sev.name(), count);
            }
        }

        long totalOpenCases = visibleClientIds.isEmpty() ? 0L
            : caseRepo.countOpenByClientIds(
                visibleClientIds, com.indira.opsconsole.domain.enums.CaseState.RESOLVED);

        return MetricsSnapshot.builder()
            .collectedAt(Instant.now())
            .pendingImports(pendingImports)
            .errorImports(errorImports)
            .streamFreshness(streamFreshness)
            .openCasesBySeverity(openBySeverity)
            .totalOpenCases(totalOpenCases)
            .build();
    }

    @Getter
    @Builder
    public static class MetricsSnapshot {
        private final Instant              collectedAt;
        private final long                 pendingImports;
        private final long                 errorImports;
        private final Map<String, Instant> streamFreshness;     // stream → latest received_at (null = never)
        private final Map<String, Long>    openCasesBySeverity; // severity → count
        private final long                 totalOpenCases;
    }
}
