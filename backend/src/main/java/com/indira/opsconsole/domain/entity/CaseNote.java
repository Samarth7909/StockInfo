package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaseNote {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ExceptionCase exceptionCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private AppUser author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * JSON snapshot of source_file IDs that were active when this note was written.
     * Preserved even if files are corrected later. Never modified after insert.
     */
    @Column(name = "evidence_version_snapshot", columnDefinition = "TEXT")
    private String evidenceVersionSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
