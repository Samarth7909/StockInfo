package com.indira.opsconsole.ingest;

import com.indira.opsconsole.domain.enums.SourceStatus;
import com.indira.opsconsole.repository.SourceFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies idempotency guarantee:
 *   Same bytes → same SHA-256 → no new SourceFile record created.
 *   Changed bytes → new version, prior record untouched.
 *
 * Uses an isolated in-memory SQLite DB (unique per test class).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:test-idempotent?mode=memory&cache=shared&uri=true"
})
@ActiveProfiles("test")
@Transactional
class IdempotentImportTest {

    @Autowired IngestService        ingestService;
    @Autowired SourceFileRepository sourceFileRepo;

    private static final String HOLDINGS_CSV =
        "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
        "CLI-IDEM-01,INE009A01021,EQUITY,INFY,EQ,100,2024-10-01T18:00:00\n";

    private static final String HOLDINGS_CSV_CHANGED =
        "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
        "CLI-IDEM-01,INE009A01021,EQUITY,INFY,EQ,200,2024-10-01T18:00:00\n";

    @Test
    @DisplayName("Re-importing identical bytes returns idempotent=true and does not create a second record")
    void sameBytes_idempotent() {
        byte[] content = HOLDINGS_CSV.getBytes();

        IngestResult first  = ingestService.ingest(req(content));
        IngestResult second = ingestService.ingest(req(content));

        assertThat(first.isIdempotent()).isFalse();
        assertThat(first.getStatus()).isEqualTo(SourceStatus.DONE);

        assertThat(second.isIdempotent()).isTrue();
        assertThat(second.getSourceFileId()).isEqualTo(first.getSourceFileId());

        assertThat(sourceFileRepo.findById(first.getSourceFileId())).isPresent();
    }

    @Test
    @DisplayName("Changed bytes create a new version; original source file still exists")
    void changedBytes_newVersion() {
        IngestResult v1 = ingestService.ingest(req(HOLDINGS_CSV.getBytes()));
        IngestResult v2 = ingestService.ingest(req(HOLDINGS_CSV_CHANGED.getBytes()));

        assertThat(v1.isIdempotent()).isFalse();
        assertThat(v2.isIdempotent()).isFalse();
        assertThat(v1.getSourceFileId()).isNotEqualTo(v2.getSourceFileId());

        assertThat(sourceFileRepo.findById(v1.getSourceFileId())).isPresent();
        assertThat(sourceFileRepo.findById(v2.getSourceFileId())).isPresent();
    }

    @Test
    @DisplayName("Row count is recorded correctly after import")
    void rowCount_correctlyRecorded() {
        IngestResult result = ingestService.ingest(req(HOLDINGS_CSV.getBytes()));
        assertThat(result.getRowCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(0);
    }

    private IngestRequest req(byte[] content) {
        return IngestRequest.builder()
            .streamName("HOLDINGS")
            .content(content)
            .filename("test_holdings_idem.csv")
            .importedByUserId(null)
            .build();
    }
}
