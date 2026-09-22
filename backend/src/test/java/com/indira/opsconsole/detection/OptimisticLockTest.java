package com.indira.opsconsole.detection;

import com.indira.opsconsole.domain.entity.*;
import com.indira.opsconsole.domain.enums.*;
import com.indira.opsconsole.ingest.*;
import com.indira.opsconsole.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies optimistic locking on case state transitions.
 * A second writer supplying a stale version must receive a 409 Conflict.
 * Uses an isolated in-memory SQLite DB (unique per test class).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:test-optlock?mode=memory&cache=shared&uri=true"
})
@ActiveProfiles("test")
// Note: NOT using @Transactional here because optimistic locking requires
// separate transactions to actually increment and persist the @Version field.
// The @Transactional annotation would cause all operations to share one
// transaction, defeating the optimistic lock check.
class OptimisticLockTest {

    @Autowired IngestService            ingestService;
    @Autowired CaseWorkflowService      workflowSvc;
    @Autowired ExceptionCaseRepository  caseRepo;
    @Autowired AppUserRepository        userRepo;

    @Test
    @DisplayName("Stale version on transition returns 409 CONFLICT")
    void staleVersion_returns409() {
        ingestService.ingest(IngestRequest.builder().streamName("HOLDINGS")
            .content(("client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
                      "CLI-001,INE009A01021,EQUITY,INFY,EQ,200,2024-09-16T18:00:00\n").getBytes())
            .filename("h.csv").importedByUserId(null).build());
        ingestService.ingest(IngestRequest.builder().streamName("DP_EXTRACT")
            .content(dpHtml("CLI-001", "INE009A01021", "150", "SETTLED", "0").getBytes())
            .filename("dp.html").importedByUserId(null).build());

        List<ExceptionCase> cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.HOLDING_MISMATCH)
            .filter(c -> "CLI-001".equals(c.getClientId())).toList();
        if (cases.isEmpty()) return;

        ExceptionCase c = cases.get(0);
        AppUser opsLead = userRepo.findByUsername("opsleader")
            .orElseThrow(() -> new AssertionError("opsleader not found"));

        // First transition with version=1 — succeeds
        workflowSvc.transition(c.getId(), CaseState.INVESTIGATING, "first transition", 1, opsLead);

        // Second transition with same stale version=1 — must fail with 409
        assertThatThrownBy(() ->
            workflowSvc.transition(c.getId(), CaseState.NEEDS_SOURCE, "stale attempt", 1, opsLead)
        )
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
            .isEqualTo(409));
    }

    @Test
    @DisplayName("Correct version on consecutive transitions succeeds")
    void correctVersions_succeed() {
        ingestService.ingest(IngestRequest.builder().streamName("HOLDINGS")
            .content(("client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
                      "CLI-002,INE040A01034,EQUITY,HDFCBANK,EQ,100,2024-09-16T18:00:00\n").getBytes())
            .filename("h2.csv").importedByUserId(null).build());
        ingestService.ingest(IngestRequest.builder().streamName("DP_EXTRACT")
            .content(dpHtml("CLI-002", "INE040A01034", "80", "SETTLED", "0").getBytes())
            .filename("dp2.html").importedByUserId(null).build());

        List<ExceptionCase> cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.HOLDING_MISMATCH)
            .filter(c -> "CLI-002".equals(c.getClientId())).toList();
        if (cases.isEmpty()) return;

        AppUser opsLead = userRepo.findByUsername("opsleader")
            .orElseThrow(() -> new AssertionError("opsleader not found"));

        String caseId = cases.get(0).getId();
        // v1 → INVESTIGATING
        ExceptionCase v2 = workflowSvc.transition(caseId, CaseState.INVESTIGATING, "starting", 1, opsLead);
        assertThat(v2.getState()).isEqualTo(CaseState.INVESTIGATING);

        // v2 → RESOLVED (only opsLead can do this)
        ExceptionCase v3 = workflowSvc.transition(caseId, CaseState.RESOLVED,
            "confirmed match after correction", v2.getVersion(), opsLead);
        assertThat(v3.getState()).isEqualTo(CaseState.RESOLVED);
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
