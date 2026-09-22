package com.indira.opsconsole.detection;

import com.indira.opsconsole.domain.enums.CaseType;
import com.indira.opsconsole.domain.enums.EvidenceState;
import com.indira.opsconsole.ingest.*;
import com.indira.opsconsole.repository.ExceptionCaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for case detection rules.
 * Uses an isolated in-memory SQLite DB (unique per test class) to prevent cross-class pollution.
 * Verifies: holding mismatch, pending DP, missing source, duplicate UTR.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:test-detection?mode=memory&cache=shared&uri=true"
})
@ActiveProfiles("test")
@Transactional
class CaseDetectionTest {

    @Autowired IngestService           ingestService;
    @Autowired ExceptionCaseRepository caseRepo;

    @Test
    @DisplayName("Nonzero holding delta generates HIGH HOLDING_MISMATCH case")
    void holdingMismatch_createsHighCase() {
        ingestCsv("HOLDINGS",
            "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
            "CLI-T01,INE009A01021,EQUITY,INFY,EQ,200,2024-09-16T18:00:00\n",
            "h.csv");

        ingestHtml("DP_EXTRACT",
            dpHtml("CLI-T01", "INE009A01021", "170", "SETTLED", "0"), "dp.html");

        var cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.HOLDING_MISMATCH)
            .filter(c -> "CLI-T01".equals(c.getClientId()))
            .toList();

        assertThat(cases).as("Expected HOLDING_MISMATCH for CLI-T01").hasSize(1);
        assertThat(cases.get(0).getQuantityDelta()).isEqualTo(30L); // 200-170
        assertThat(cases.get(0).getSeverity().name()).isEqualTo("HIGH");
        assertThat(cases.get(0).getEvidenceState()).isEqualTo(EvidenceState.UNMATCHED_IDENTITY);
    }

    @Test
    @DisplayName("Zero holding delta does NOT create a mismatch case")
    void matchingHolding_noCase() {
        ingestCsv("HOLDINGS",
            "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
            "CLI-T02,INE009A01021,EQUITY,INFY,EQ,150,2024-09-16T18:00:00\n", "h.csv");

        ingestHtml("DP_EXTRACT",
            dpHtml("CLI-T02", "INE009A01021", "150", "SETTLED", "0"), "dp.html");

        var cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.HOLDING_MISMATCH)
            .filter(c -> "CLI-T02".equals(c.getClientId()))
            .toList();
        assertThat(cases).isEmpty();
    }

    @Test
    @DisplayName("PENDING DP row creates PENDING_DP MEDIUM case")
    void pendingDp_createsMediumCase() {
        ingestHtml("DP_EXTRACT",
            dpHtml("CLI-T03", "INE040A01034", "0", "PENDING", "20"), "dp.html");

        var cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.PENDING_DP)
            .filter(c -> "CLI-T03".equals(c.getClientId()))
            .toList();

        assertThat(cases).as("Expected PENDING_DP case for CLI-T03").hasSize(1);
        assertThat(cases.get(0).getSeverity().name()).isEqualTo("MEDIUM");
        assertThat(cases.get(0).getEvidenceState()).isEqualTo(EvidenceState.UNKNOWN);
    }

    @Test
    @DisplayName("Internal holding with no DP counterpart creates MISSING_SOURCE MEDIUM case")
    void missingDp_createsMissingSourceCase() {
        ingestCsv("HOLDINGS",
            "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
            "CLI-T04,INE062A01020,EQUITY,SBIN,EQ,80,2024-09-16T18:00:00\n", "h.csv");
        // No DP file ingested for CLI-T04/INE062A01020

        var cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.MISSING_SOURCE)
            .filter(c -> "CLI-T04".equals(c.getClientId()))
            .toList();

        assertThat(cases).as("Expected MISSING_SOURCE case for CLI-T04").hasSize(1);
        assertThat(cases.get(0).getEvidenceState()).isEqualTo(EvidenceState.MISSING_SOURCE);
    }

    @Test
    @DisplayName("Duplicate UTR in bank entries creates CASH_RECONCILIATION CONFLICTING_EVIDENCE case")
    void duplicateUtr_conflictingEvidenceCase() {
        String bankCsv =
            "CLIENT_ID,UTR_REFERENCE,DIRECTION,AMOUNT_PAISE,BANK_STATUS,VALUE_DATE,NARRATION\n" +
            "CLI-T05,UTRDUP001,CREDIT,30000000,PENDING,2024-09-16,Transfer\n" +
            "CLI-T05,UTRDUP001,CREDIT,30000000,PENDING,2024-09-16,Duplicate UTR\n";
        ingestCsv("BANK_CONFIRMATION", bankCsv, "bank.csv");

        var cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.CASH_RECONCILIATION)
            .toList();

        assertThat(cases).as("Expected CASH_RECONCILIATION case for duplicate UTR").isNotEmpty();
        assertThat(cases.get(0).getEvidenceState()).isEqualTo(EvidenceState.CONFLICTING_EVIDENCE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ingestCsv(String stream, String csv, String filename) {
        ingestService.ingest(IngestRequest.builder()
            .streamName(stream).content(csv.getBytes())
            .filename(filename).importedByUserId(null).build());
    }

    private void ingestHtml(String stream, String html, String filename) {
        ingestService.ingest(IngestRequest.builder()
            .streamName(stream).content(html.getBytes())
            .filename(filename).importedByUserId(null).build());
    }

    private String dpHtml(String clientId, String isin, String settledQty,
                           String movState, String pendingQty) {
        return "<!DOCTYPE html><html><body><table id='dp-positions'>" +
            "<thead><tr><th>CLIENT_ID</th><th>ISIN</th><th>POSITION_TYPE</th>" +
            "<th>EXCHANGE_SYMBOL</th><th>SETTLED_QTY</th><th>MOVEMENT_STATE</th>" +
            "<th>PENDING_QTY</th><th>CUT_AT</th></tr></thead><tbody>" +
            "<tr><td>" + clientId + "</td><td>" + isin + "</td><td>EQUITY</td>" +
            "<td>TEST</td><td>" + settledQty + "</td><td>" + movState + "</td>" +
            "<td>" + pendingQty + "</td><td>2024-09-16T18:00:00</td></tr>" +
            "</tbody></table></body></html>";
    }
}
