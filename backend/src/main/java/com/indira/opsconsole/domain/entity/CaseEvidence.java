package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_evidence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaseEvidence {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ExceptionCase exceptionCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private SourceFile sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_row_id")
    private RawRow rawRow;

    @Column(name = "mapping_version", nullable = false)
    @Builder.Default
    private String mappingVersion = "v1";

    /**
     * INTERNAL_HOLDING, DP_HOLDING, CASH_EVENT, BANK_ENTRY, EXCHANGE_REF
     */
    @Column(name = "evidence_role", nullable = false)
    private String evidenceRole;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (addedAt == null) addedAt = Instant.now();
    }
}
