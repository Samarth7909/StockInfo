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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that investigator notes survive a file correction (re-import with changed bytes).
 *
 * Domain rule 6: "Do not destroy a prior investigator note."
 * Uses an isolated in-memory SQLite DB (unique per test class).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:test-notepreserv?mode=memory&cache=shared&uri=true"
})
@ActiveProfiles("test")
@Transactional
class NotePreservationTest {

    @Autowired IngestService            ingestService;
    @Autowired CaseWorkflowService      workflowSvc;
    @Autowired ExceptionCaseRepository  caseRepo;
    @Autowired CaseNoteRepository       noteRepo;
    @Autowired AppUserRepository        userRepo;

    private static final byte[] HOLDINGS_V1 =
        ("client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
         "CLI-001,INE040A01034,EQUITY,HDFCBANK,EQ,200,2024-09-16T18:00:00\n")
        .getBytes();

    private static final byte[] DP_V1 =
        ("<!DOCTYPE html><html><body><table id='dp-positions'>" +
         "<thead><tr><th>CLIENT_ID</th><th>ISIN</th><th>POSITION_TYPE</th>" +
         "<th>EXCHANGE_SYMBOL</th><th>SETTLED_QTY</th><th>MOVEMENT_STATE</th>" +
         "<th>PENDING_QTY</th><th>CUT_AT</th></tr></thead><tbody>" +
         "<tr><td>CLI-001</td><td>INE040A01034</td><td>EQUITY</td><td>HDFCBANK</td>" +
         "<td>180</td><td>SETTLED</td><td>0</td><td>2024-09-16T18:00:00</td></tr>" +
         "</tbody></table></body></html>")
        .getBytes();

    private static final byte[] DP_V2_CORRECTED =
        ("<!DOCTYPE html><html><body><table id='dp-positions'>" +
         "<thead><tr><th>CLIENT_ID</th><th>ISIN</th><th>POSITION_TYPE</th>" +
         "<th>EXCHANGE_SYMBOL</th><th>SETTLED_QTY</th><th>MOVEMENT_STATE</th>" +
         "<th>PENDING_QTY</th><th>CUT_AT</th></tr></thead><tbody>" +
         "<tr><td>CLI-001</td><td>INE040A01034</td><td>EQUITY</td><td>HDFCBANK</td>" +
         "<td>200</td><td>SETTLED</td><td>0</td><td>2024-09-16T18:00:00</td></tr>" +
         "</tbody></table></body></html>")
        .getBytes();

    @Test
    @DisplayName("Investigator note survives after a corrected DP file is re-imported")
    void note_survivesFileCorrection() {
        ingestService.ingest(IngestRequest.builder()
            .streamName("HOLDINGS").content(HOLDINGS_V1)
            .filename("holdings_v1.csv").importedByUserId(null).build());
        ingestService.ingest(IngestRequest.builder()
            .streamName("DP_EXTRACT").content(DP_V1)
            .filename("dp_v1.html").importedByUserId(null).build());

        List<ExceptionCase> cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.HOLDING_MISMATCH)
            .filter(c -> "CLI-001".equals(c.getClientId()))
            .toList();
        assertThat(cases).as("Expected a HOLDING_MISMATCH case for CLI-001").isNotEmpty();
        ExceptionCase mismatchCase = cases.get(0);

        AppUser investigator = userRepo.findByUsername("invest1")
            .orElseThrow(() -> new AssertionError("invest1 user not found — DataSeeder may not have run"));
        workflowSvc.addNote(mismatchCase.getId(),
            "Pending DP movement might explain delta — awaiting settlement confirmation.",
            investigator);

        List<CaseNote> notesBefore =
            noteRepo.findByExceptionCaseIdOrderByCreatedAtAsc(mismatchCase.getId());
        assertThat(notesBefore).hasSize(1);
        String savedNoteId = notesBefore.get(0).getId();

        IngestResult v2Result = ingestService.ingest(IngestRequest.builder()
            .streamName("DP_EXTRACT").content(DP_V2_CORRECTED)
            .filename("dp_v2_corrected.html").importedByUserId(null).build());
        assertThat(v2Result.isIdempotent()).isFalse();

        List<CaseNote> notesAfter =
            noteRepo.findByExceptionCaseIdOrderByCreatedAtAsc(mismatchCase.getId());
        assertThat(notesAfter).as("Note must survive file correction").hasSize(1);
        assertThat(notesAfter.get(0).getId()).isEqualTo(savedNoteId);
        assertThat(notesAfter.get(0).getBody())
            .contains("Pending DP movement might explain delta");

        String snapshot = notesAfter.get(0).getEvidenceVersionSnapshot();
        assertThat(snapshot).as("Evidence snapshot must reference old file").isNotNull();
    }

    @Test
    @DisplayName("Re-importing identical DP bytes does not duplicate notes")
    void idempotentReimport_doesNotDuplicateNotes() {
        ingestService.ingest(IngestRequest.builder()
            .streamName("HOLDINGS").content(HOLDINGS_V1)
            .filename("holdings.csv").importedByUserId(null).build());
        ingestService.ingest(IngestRequest.builder()
            .streamName("DP_EXTRACT").content(DP_V1)
            .filename("dp.html").importedByUserId(null).build());

        List<ExceptionCase> cases = caseRepo.findAll().stream()
            .filter(c -> c.getCaseType() == CaseType.HOLDING_MISMATCH).toList();
        if (cases.isEmpty()) return;

        AppUser investigator = userRepo.findByUsername("invest1")
            .orElseThrow(() -> new AssertionError("invest1 not found"));
        workflowSvc.addNote(cases.get(0).getId(), "Initial note", investigator);

        ingestService.ingest(IngestRequest.builder()
            .streamName("DP_EXTRACT").content(DP_V1)
            .filename("dp.html").importedByUserId(null).build());

        List<CaseNote> notes =
            noteRepo.findByExceptionCaseIdOrderByCreatedAtAsc(cases.get(0).getId());
        assertThat(notes).as("Idempotent reimport must not duplicate notes").hasSize(1);
    }
}
