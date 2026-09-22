package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankEntry {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private SourceFile sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_row_id")
    private RawRow rawRow;

    @Column(name = "client_id")
    private String clientId;

    /** UTR or bank reference. May be duplicated — never assume uniqueness. */
    @Column(name = "utr_reference")
    private String utrReference;

    @Column
    private String direction;  // CREDIT or DEBIT

    /** Amount in paise (always positive; direction indicates sign). */
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "bank_status")
    private String bankStatus;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(columnDefinition = "TEXT")
    private String narration;

    /**
     * A bank entry can support a cash case ONLY after a human (or documented rule)
     * has verified client, direction, amount, reference, and timing.
     * Default false — never auto-matched.
     */
    @Column(name = "human_matched", nullable = false)
    @Builder.Default
    private boolean humanMatched = false;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
