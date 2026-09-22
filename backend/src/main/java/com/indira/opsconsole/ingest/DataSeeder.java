package com.indira.opsconsole.ingest;

import com.indira.opsconsole.domain.entity.AppUser;
import com.indira.opsconsole.domain.enums.UserRole;
import com.indira.opsconsole.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

/**
 * Runs on startup to:
 * 1. Upsert demo users with proper BCrypt hashes (overrides the placeholder V6 SQL hashes).
 * 2. Auto-import seed files from classpath:seed-data/ when running in dev mode.
 *
 * Seeds are idempotent: same file → same hash → no-op import.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final AppUserRepository userRepo;
    private final PasswordEncoder   passwordEncoder;
    private final IngestService     ingestService;

    private static final String SEED_USER_ID = "u-003";  // opsleader runs the seed imports

    @Override
    public void run(ApplicationArguments args) throws Exception {
        upsertDemoUsers();
        importSeedFiles();
    }

    // ── Demo users ────────────────────────────────────────────────────────────

    private void upsertDemoUsers() {
        upsert("u-001", "support1",  "support123", UserRole.SUPPORT,      "Support Agent One",  "DESK-A",
               Set.of("CLI-001", "CLI-002"));
        upsert("u-002", "invest1",   "invest123",  UserRole.INVESTIGATOR,  "Investigator One",   "DESK-A",
               Set.of("CLI-001", "CLI-002", "CLI-003"));
        upsert("u-003", "opsleader", "ops123",     UserRole.OPS_LEAD,     "Operations Lead",    "DESK-A",
               Set.of("CLI-001", "CLI-002", "CLI-003", "CLI-004"));
        upsert("u-004", "auditor1",  "audit123",   UserRole.AUDITOR,      "Auditor One",        "DESK-A",
               Set.of("CLI-001", "CLI-002", "CLI-003", "CLI-004"));
        log.info("Demo users seeded.");
    }

    private void upsert(String id, String username, String rawPassword, UserRole role,
                        String fullName, String deskId, Set<String> clientIds) {
        // Always update the password hash so the real BCrypt replaces the placeholder from V6 SQL.
        // Other fields are only set on first insert.
        AppUser existing = userRepo.findByUsername(username).orElse(null);
        if (existing != null) {
            existing.setPasswordHash(passwordEncoder.encode(rawPassword));
            userRepo.save(existing);
            log.debug("Updated BCrypt hash for user: {}", username);
            return;
        }
        AppUser user = AppUser.builder()
            .id(id)
            .username(username)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .fullName(fullName)
            .deskId(deskId)
            .accessibleClientIds(clientIds)
            .build();
        userRepo.save(user);
        log.debug("Seeded user: {}", username);
    }

    // ── Seed file imports ─────────────────────────────────────────────────────

    private void importSeedFiles() {
        importIfPresent("seed-data/holdings_snapshot.csv",    "HOLDINGS",          "holdings_snapshot.csv");
        importIfPresent("seed-data/dp_extract.html",          "DP_EXTRACT",         "dp_extract.html");
        importIfPresent("seed-data/cash_ledger.jsonl",        "CASH_LEDGER",        "cash_ledger.jsonl");
        importIfPresent("seed-data/bank_confirmation.csv",    "BANK_CONFIRMATION",  "bank_confirmation.csv");
        importIfPresent("seed-data/exchange_reference.csv",   "EXCHANGE_REF",       "exchange_reference.csv");
        importIfPresent("seed-data/access_scopes.json",       "ACCESS_SCOPES",       "access_scopes.json");
    }

    private void importIfPresent(String classpathPath, String streamName, String filename) {
        try {
            ClassPathResource res = new ClassPathResource(classpathPath);
            if (!res.exists()) {
                log.warn("Seed file not found on classpath: {}; skipping.", classpathPath);
                return;
            }
            try (InputStream is = res.getInputStream()) {
                byte[] content = is.readAllBytes();
                IngestRequest req = IngestRequest.builder()
                    .streamName(streamName)
                    .content(content)
                    .filename(filename)
                    .importedByUserId(SEED_USER_ID)
                    .build();
                IngestResult result = ingestService.ingest(req);
                if (result.isIdempotent()) {
                    log.info("Seed already imported (idempotent): {}", filename);
                } else {
                    log.info("Seed imported: {} rows={} errors={} casesCreated={}",
                        filename, result.getRowCount(), result.getErrorCount(), result.getCasesCreated());
                }
            }
        } catch (Exception e) {
            log.error("Failed to import seed file {}: {}", classpathPath, e.getMessage(), e);
        }
    }
}
