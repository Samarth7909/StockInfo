package com.indira.opsconsole.controller;

import com.indira.opsconsole.controller.dto.ImportResponse;
import com.indira.opsconsole.domain.entity.AppUser;
import com.indira.opsconsole.domain.entity.SourceFile;
import com.indira.opsconsole.ingest.IngestRequest;
import com.indira.opsconsole.ingest.IngestResult;
import com.indira.opsconsole.ingest.IngestService;
import com.indira.opsconsole.repository.SourceFileRepository;
import com.indira.opsconsole.security.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * POST /api/imports        — ingest a file (OPS_LEAD only, enforced by SecurityConfig)
 * GET  /api/imports        — list all imports (paginated)
 * GET  /api/imports/:id    — single import detail
 */
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final IngestService        ingestService;
    private final SourceFileRepository sourceFileRepo;
    private final AuthHelper           authHelper;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ImportResponse> ingestFile(
            @RequestParam("file")       MultipartFile file,
            @RequestParam("streamName") String streamName) {

        AppUser actor = authHelper.currentUser();

        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read file: " + e.getMessage());
        }

        IngestRequest req = IngestRequest.builder()
            .streamName(streamName.toUpperCase())
            .content(content)
            .filename(file.getOriginalFilename())
            .importedByUserId(actor.getId())
            .build();

        IngestResult result = ingestService.ingest(req);

        HttpStatus status = result.isIdempotent() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(toResponse(result, actor));
    }

    @GetMapping
    public ResponseEntity<List<ImportResponse>> listImports() {
        authHelper.currentUser(); // ensure authenticated
        List<ImportResponse> list = sourceFileRepo.findAllOrderByReceivedAtDesc()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportResponse> getImport(@PathVariable String id) {
        authHelper.currentUser();
        SourceFile sf = sourceFileRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found: " + id));
        return ResponseEntity.ok(toResponse(sf));
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private ImportResponse toResponse(IngestResult result, AppUser actor) {
        // Reload the source file to get all fields
        SourceFile sf = sourceFileRepo.findById(result.getSourceFileId()).orElse(null);
        if (sf == null) {
            return ImportResponse.builder()
                .id(result.getSourceFileId())
                .idempotent(result.isIdempotent())
                .status(result.getStatus())
                .rowCount(result.getRowCount())
                .errorCount(result.getErrorCount())
                .casesCreated(result.getCasesCreated())
                .casesReopened(result.getCasesReopened())
                .errorMessages(result.getErrorMessages())
                .importedBy(actor.getUsername())
                .build();
        }
        return ImportResponse.builder()
            .id(sf.getId())
            .streamName(sf.getStreamName())
            .cutAt(sf.getCutAt())
            .receivedAt(sf.getReceivedAt())
            .filename(sf.getFilename())
            .sha256Hash(sf.getSha256Hash())
            .schemaVersion(sf.getSchemaVersion())
            .mappingVersion(sf.getMappingVersion())
            .rowCount(sf.getRowCount())
            .errorCount(sf.getErrorCount())
            .status(sf.getStatus())
            .errorDetail(sf.getErrorDetail())
            .idempotent(result.isIdempotent())
            .casesCreated(result.getCasesCreated())
            .casesReopened(result.getCasesReopened())
            .errorMessages(result.getErrorMessages() != null ? result.getErrorMessages() : List.of())
            .importedBy(sf.getImportedBy() != null ? sf.getImportedBy().getUsername() : null)
            .build();
    }

    private ImportResponse toResponse(SourceFile sf) {
        return ImportResponse.builder()
            .id(sf.getId())
            .streamName(sf.getStreamName())
            .cutAt(sf.getCutAt())
            .receivedAt(sf.getReceivedAt())
            .filename(sf.getFilename())
            .sha256Hash(sf.getSha256Hash())
            .schemaVersion(sf.getSchemaVersion())
            .mappingVersion(sf.getMappingVersion())
            .rowCount(sf.getRowCount())
            .errorCount(sf.getErrorCount())
            .status(sf.getStatus())
            .errorDetail(sf.getErrorDetail())
            .idempotent(false)
            .casesCreated(0)
            .casesReopened(0)
            .errorMessages(List.of())
            .importedBy(sf.getImportedBy() != null ? sf.getImportedBy().getUsername() : null)
            .build();
    }
}
