package com.indira.opsconsole.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "raw_rows")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RawRow {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private SourceFile sourceFile;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    /** Original row serialised as JSON string. Never modified after insert. */
    @Column(name = "raw_json", nullable = false, columnDefinition = "TEXT")
    private String rawJson;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
