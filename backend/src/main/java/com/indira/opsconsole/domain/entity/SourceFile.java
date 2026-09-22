package com.indira.opsconsole.domain.entity;

import com.indira.opsconsole.domain.enums.SourceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SourceFile {

    @Id
    private String id;

    /** Stream name: HOLDINGS, DP_EXTRACT, CASH_LEDGER, BANK_CONFIRMATION, EXCHANGE_REF, NSE_BHAVCOPY, BSE_BHAVCOPY */
    @Column(name = "stream_name", nullable = false)
    private String streamName;

    /** The report cut timestamp extracted from the file content (may be null if not determinable). */
    @Column(name = "cut_at")
    private Instant cutAt;

    /** When this file was received by the system. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private String filename;

    @Column(name = "sha256_hash", nullable = false)
    private String sha256Hash;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private String schemaVersion = "v1";

    @Column(name = "row_count", nullable = false)
    @Builder.Default
    private int rowCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private int errorCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SourceStatus status = SourceStatus.PENDING;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by")
    private AppUser importedBy;

    @Column(name = "mapping_version", nullable = false)
    @Builder.Default
    private String mappingVersion = "v1";

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (receivedAt == null) receivedAt = Instant.now();
    }
}
