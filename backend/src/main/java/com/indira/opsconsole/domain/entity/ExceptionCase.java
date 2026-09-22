package com.indira.opsconsole.domain.entity;

import com.indira.opsconsole.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExceptionCase {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false)
    private CaseType caseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CaseState state = CaseState.OPEN;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column
    private String isin;

    @Column(name = "cut_at")
    private Instant cutAt;

    /** nonzero = holding mismatch; null for cash/missing-source cases */
    @Column(name = "quantity_delta")
    private Long quantityDelta;

    /** in paise; null for holding cases */
    @Column(name = "amount_delta_paise")
    private Long amountDeltaPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_state", nullable = false)
    @Builder.Default
    private EvidenceState evidenceState = EvidenceState.UNKNOWN;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Optimistic lock version — incremented on every state change.
     * Concurrent transitions must supply the expected version.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "exceptionCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CaseEvidence> evidenceLinks = new ArrayList<>();

    @OneToMany(mappedBy = "exceptionCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<CaseNote> notes = new ArrayList<>();

    @OneToMany(mappedBy = "exceptionCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    @Builder.Default
    private List<CaseTransition> transitions = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
