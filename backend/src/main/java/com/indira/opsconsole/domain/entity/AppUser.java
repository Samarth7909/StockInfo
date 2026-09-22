package com.indira.opsconsole.domain.entity;

import com.indira.opsconsole.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "desk_id")
    private String deskId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Set of client_ids this user is permitted to access. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_client_scopes",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "client_id")
    @Builder.Default
    private Set<String> accessibleClientIds = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
