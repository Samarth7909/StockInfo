package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cash_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashEvent {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private SourceFile sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_row_id")
    private RawRow rawRow;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "event_id")
    private String eventId;

    /**
     * Signed integer in paise (1 INR = 100 paise).
     * Positive = credit; negative = debit.
     * Never stored as float.
     */
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    /** POSTED or PENDING. Only POSTED events <= cutAt count toward balance. */
    @Column(nullable = false)
    private String state;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
