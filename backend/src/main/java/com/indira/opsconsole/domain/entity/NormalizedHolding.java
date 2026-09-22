package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "normalized_holdings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NormalizedHolding {

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

    @Column(nullable = false)
    private String isin;

    @Column(name = "position_type", nullable = false)
    private String positionType;

    @Column(name = "cut_at", nullable = false)
    private Instant cutAt;

    @Column(name = "settled_qty", nullable = false)
    private long settledQty;

    @Column(name = "exchange_symbol")
    private String exchangeSymbol;

    private String series;

    /** 'INTERNAL' (broker snapshot) or 'DP' (depository extract settled row). */
    @Column(name = "data_source", nullable = false)
    private String dataSource;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
