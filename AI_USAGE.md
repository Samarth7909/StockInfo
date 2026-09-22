# AI_USAGE.md

## What AI (Kiro/Claude) helped draft

| Area | AI involvement | What I verified myself |
|------|---------------|------------------------|
| Project scaffold & pom.xml | AI generated full Maven POM with all dependencies | Dependency versions checked against Maven Central; scope correctness reviewed |
| Flyway SQL migrations | AI drafted all CREATE TABLE statements | Reviewed column types, CHECK constraints, index definitions, foreign keys |
| Seed data files | AI generated synthetic CSV/HTML/JSONL content | Verified mismatches are intentional (CLI-001/HDFCBANK delta=20, duplicate UTR) |
| JPA entities | AI drafted all @Entity classes and repositories | Reviewed @Version optimistic lock, @PrePersist hooks, relationship fetch types |
| Stream adapters | AI drafted all five adapters | Reviewed normalization logic: paise-as-integer, ISIN-only joins, pending≠settled |
| IngestService | AI drafted pipeline with idempotency pattern | Reviewed concurrent-insert collision handling, setter injection for circular dep |
| CaseDetectionService | AI drafted four detection rules | Verified rules match domain spec: zero delta = no case, pending doesn't erase, missing = UNKNOWN |
| Spring Security config | AI drafted JWT filter + SecurityConfig | Reviewed route-level role checks, stateless session, CORS allowlist |
| REST controllers | AI drafted all controllers and DTOs | Reviewed scope filtering at query layer, SUPPORT masking, optimistic lock 409 |
| React frontend | AI drafted all pages and components | Reviewed: UNKNOWN badge shown for missing/unknown states, OPS_LEAD-only RESOLVED gate, restricted states |
| decisions.md | AI drafted based on requirements | Human-reviewed all assumptions and UNKNOWN case descriptions |
| JUnit tests | AI drafted test structure | Reviewed test assertions for correctness against actual business rules |

## What I verified and would defend in review

- The paise arithmetic: all `amount_paise` fields are `long`, never `double`. `parseAmountPaise` rejects floating-point JSON values with an explicit quarantine error.
- The ISIN-only join rule: no adapter or detection rule joins on `exchange_symbol`. Symbol is stored as metadata but never used as a key.
- The idempotency guarantee: the unique index on `(stream_name, sha256_hash)` in V2 migration is the final arbiter, not just application-level logic. A race between two concurrent workers resolves via constraint violation, not last-write-wins.
- PENDING ≠ SETTLED: `DPHtmlAdapter` emits `row_type=PENDING` for PENDING rows and the `RowPersistenceService` routes them to `dp_pending_movements`, never to `normalized_holdings`.
- The optimistic lock: `ExceptionCase` uses JPA `@Version` on the `version` column. The controller requires `expectedVersion` in the transition request body. A mismatch returns HTTP 409.
- SUPPORT masking enforced at API: `CaseController.toSummary()` and `getCase()` both call `authHelper.maskClientId()` and null out `amountDeltaPaise` and `rawJson` before serialising. There is no path where a SUPPORT user sees raw identifiers via the API.

## Parts not AI-verified

- Live NSE/BSE/SEBI portal artifact ingestion: manual download paths documented in decisions.md; adapters are present but not exercised against live URLs.
- Performance benchmarks: the 100k ledger / 10k holdings load test is not yet run. The generator exists but measurements are not claimed.
- BCrypt hashes in V6 SQL: the placeholder hashes in the migration file are overwritten by `DataSeeder` on startup with real BCrypt encoding. The SQL file hashes are not valid credentials on their own.
