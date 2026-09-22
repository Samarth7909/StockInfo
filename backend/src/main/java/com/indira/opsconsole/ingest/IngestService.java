package com.indira.opsconsole.ingest;

import com.indira.opsconsole.domain.entity.AppUser;
import com.indira.opsconsole.domain.entity.SourceFile;
import com.indira.opsconsole.domain.enums.SourceStatus;
import com.indira.opsconsole.ingest.adapter.AdapterRegistry;
import com.indira.opsconsole.ingest.adapter.ParseResult;
import com.indira.opsconsole.ingest.adapter.StreamAdapter;
import com.indira.opsconsole.repository.AppUserRepository;
import com.indira.opsconsole.repository.SourceFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates the full ingest pipeline:
 * 1. Compute SHA-256 hash → idempotency check
 * 2. Persist SourceFile record (status=PENDING)
 * 3. Dispatch to the correct StreamAdapter → ParseResult
 * 4. Persist raw rows + domain rows via RowPersistenceService
 * 5. Trigger CaseDetectionService for the new cut
 * 6. Update SourceFile status → DONE or ERROR
 *
 * Thread-safety: the idempotency check and source_file insert use a UNIQUE
 * constraint (stream_name + sha256_hash) as the final arbiter. Two concurrent
 * workers ingesting the same file will both attempt the insert; one will get a
 * constraint violation and return the existing import ID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final SourceFileRepository  sourceFileRepo;
    private final AppUserRepository     userRepo;
    private final AdapterRegistry       adapterRegistry;
    private final RowPersistenceService rowPersistSvc;

    // Injected lazily to avoid circular dependency; set via setter injection in config
    private com.indira.opsconsole.detection.CaseDetectionService caseDetectionService;

    public void setCaseDetectionService(
            com.indira.opsconsole.detection.CaseDetectionService svc) {
        this.caseDetectionService = svc;
    }

    /**
     * Synchronous ingest — used by tests and direct controller calls.
     */
    @Transactional
    public IngestResult ingest(IngestRequest request) {
        String hash = HashUtil.sha256Hex(request.getContent());

        // ── Idempotency: same stream + same hash → return existing record ──────
        Optional<SourceFile> existing =
            sourceFileRepo.findByStreamNameAndSha256Hash(request.getStreamName(), hash);
        if (existing.isPresent()) {
            SourceFile sf = existing.get();
            log.info("Idempotent import: stream={} hash={} existingId={}",
                request.getStreamName(), hash, sf.getId());
            return IngestResult.builder()
                .sourceFileId(sf.getId())
                .idempotent(true)
                .status(sf.getStatus())
                .rowCount(sf.getRowCount())
                .errorCount(sf.getErrorCount())
                .casesCreated(0)
                .casesReopened(0)
                .errorMessages(List.of())
                .build();
        }

        // ── Resolve adapter ───────────────────────────────────────────────────
        StreamAdapter adapter = adapterRegistry.find(request.getStreamName())
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown stream: " + request.getStreamName() +
                ". Known: " + adapterRegistry.knownStreamNames()));

        // ── Resolve importing user ────────────────────────────────────────────
        AppUser importingUser = request.getImportedByUserId() != null
            ? userRepo.findById(request.getImportedByUserId()).orElse(null)
            : null;

        // ── Create SourceFile record (status=PENDING) ──────────────────────
        SourceFile sourceFile = SourceFile.builder()
            .streamName(request.getStreamName())
            .filename(request.getFilename())
            .sha256Hash(hash)
            .importedBy(importingUser)
            .status(SourceStatus.PENDING)
            .build();

        try {
            sourceFile = sourceFileRepo.save(sourceFile);
        } catch (Exception e) {
            // Concurrent duplicate insert — re-check and return existing
            Optional<SourceFile> race =
                sourceFileRepo.findByStreamNameAndSha256Hash(request.getStreamName(), hash);
            if (race.isPresent()) {
                SourceFile sf = race.get();
                return IngestResult.builder()
                    .sourceFileId(sf.getId())
                    .idempotent(true)
                    .status(sf.getStatus())
                    .rowCount(sf.getRowCount())
                    .errorCount(sf.getErrorCount())
                    .casesCreated(0).casesReopened(0)
                    .errorMessages(List.of())
                    .build();
            }
            throw e;
        }

        // ── Parse ─────────────────────────────────────────────────────────────
        sourceFile.setStatus(SourceStatus.PROCESSING);
        sourceFileRepo.save(sourceFile);

        ParseResult parseResult;
        try {
            parseResult = adapter.parse(request.getContent(), request.getFilename());
        } catch (Exception e) {
            log.error("Adapter parse failed for {}: {}", request.getStreamName(), e.getMessage(), e);
            sourceFile.setStatus(SourceStatus.ERROR);
            sourceFile.setErrorDetail("Adapter failure: " + e.getMessage());
            sourceFileRepo.save(sourceFile);
            return IngestResult.builder()
                .sourceFileId(sourceFile.getId())
                .idempotent(false)
                .status(SourceStatus.ERROR)
                .rowCount(0).errorCount(1).casesCreated(0).casesReopened(0)
                .errorDetail(e.getMessage())
                .errorMessages(List.of(e.getMessage()))
                .build();
        }

        // ── Update detected cut on SourceFile ─────────────────────────────────
        if (parseResult.getDetectedCut() != null) {
            sourceFile.setCutAt(parseResult.getDetectedCut());
        }

        // ── Persist raw rows and domain rows ──────────────────────────────────
        try {
            persistDomainRows(request.getStreamName(), parseResult, sourceFile);
        } catch (Exception e) {
            log.error("Domain row persistence failed for {}: {}", request.getStreamName(), e.getMessage(), e);
            sourceFile.setStatus(SourceStatus.ERROR);
            sourceFile.setErrorDetail("Persistence failure: " + e.getMessage());
            sourceFile.setRowCount(parseResult.getRawRows().size());
            sourceFile.setErrorCount(parseResult.getErrors().size() + 1);
            sourceFileRepo.save(sourceFile);
            return IngestResult.builder()
                .sourceFileId(sourceFile.getId())
                .idempotent(false)
                .status(SourceStatus.ERROR)
                .rowCount(parseResult.getRawRows().size())
                .errorCount(parseResult.getErrors().size() + 1)
                .casesCreated(0).casesReopened(0)
                .errorDetail(e.getMessage())
                .errorMessages(List.of(e.getMessage()))
                .build();
        }

        // ── Finalise SourceFile ────────────────────────────────────────────────
        sourceFile.setRowCount(parseResult.getRawRows().size());
        sourceFile.setErrorCount(parseResult.getErrors().size());
        sourceFile.setStatus(SourceStatus.DONE);
        sourceFileRepo.save(sourceFile);

        // ── Trigger case detection ─────────────────────────────────────────────
        int casesCreated  = 0;
        int casesReopened = 0;
        if (caseDetectionService != null && sourceFile.getCutAt() != null) {
            var detectionResult = caseDetectionService.detectForCut(sourceFile.getCutAt(), sourceFile);
            casesCreated  = detectionResult.getCasesCreated();
            casesReopened = detectionResult.getCasesReopened();
        }

        List<String> errorMessages = parseResult.getErrors().stream()
            .map(e -> String.format("Row %d [%s]: %s (raw=%s)",
                e.getRowIndex(), e.getField(), e.getMessage(), e.getRawValue()))
            .collect(Collectors.toList());

        log.info("Ingest complete: stream={} sourceFileId={} rows={} errors={} casesCreated={} casesReopened={}",
            request.getStreamName(), sourceFile.getId(),
            parseResult.getRawRows().size(), parseResult.getErrors().size(),
            casesCreated, casesReopened);

        return IngestResult.builder()
            .sourceFileId(sourceFile.getId())
            .idempotent(false)
            .status(SourceStatus.DONE)
            .rowCount(parseResult.getRawRows().size())
            .errorCount(parseResult.getErrors().size())
            .casesCreated(casesCreated)
            .casesReopened(casesReopened)
            .errorMessages(errorMessages)
            .build();
    }

    /**
     * Async variant — returns immediately with the sourceFileId while processing
     * continues in a background thread. Used for large file uploads.
     */
    @Async
    public CompletableFuture<IngestResult> ingestAsync(IngestRequest request) {
        return CompletableFuture.completedFuture(ingest(request));
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    private void persistDomainRows(String streamName, ParseResult result, SourceFile sourceFile) {
        switch (streamName) {
            case "HOLDINGS"         -> rowPersistSvc.persistHoldingsRows(result.getRawRows(), sourceFile);
            case "DP_EXTRACT"       -> rowPersistSvc.persistDpRows(result.getRawRows(), sourceFile);
            case "CASH_LEDGER"      -> rowPersistSvc.persistCashRows(result.getRawRows(), sourceFile);
            case "BANK_CONFIRMATION"-> rowPersistSvc.persistBankRows(result.getRawRows(), sourceFile);
            case "EXCHANGE_REF"     -> rowPersistSvc.persistExchangeRows(result.getRawRows(), sourceFile);
            case "ACCESS_SCOPES"    -> rowPersistSvc.persistRawRows(result.getRawRows(), sourceFile);
            default -> log.warn("No domain persistence for stream: {}; raw rows stored only", streamName);
        }
    }
}
