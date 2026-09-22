package com.indira.opsconsole.domain.entity;

import com.indira.opsconsole.domain.enums.CaseState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_transitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaseTransition {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ExceptionCase exceptionCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false)
    private CaseState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private CaseState toState;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "evidence_version", nullable = false)
    private int evidenceVersion;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (changedAt == null) changedAt = Instant.now();
    }
}
