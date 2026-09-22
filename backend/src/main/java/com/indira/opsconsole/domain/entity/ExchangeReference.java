package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exchange_reference")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExchangeReference {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private SourceFile sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_row_id")
    private RawRow rawRow;

    /** ISIN is the only universal security key. Symbol is metadata. */
    @Column(nullable = false)
    private String isin;

    /** Display symbol — may be renamed, reused, or differ across venues. */
    @Column
    private String symbol;

    @Column
    private String series;

    @Column
    private String exchange;  // NSE or BSE

    /** Reference price in paise. Not evidence of account entitlement. */
    @Column(name = "reference_price_paise")
    private Long referencePricePaise;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
