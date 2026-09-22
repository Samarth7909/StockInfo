# Ops Exception Console

A read-only operations console for an Indian stock broker's exception desk.
Ingests five synthetic data streams, detects holding and cash discrepancies,
and provides a role-gated case management workflow.

> **Assessment demo — all data is synthetic. No real client records, broker credentials, or live market APIs are used.**

---

## Quick Start (two commands)

### 1. Start the application

```bash
# From project root
cd backend
./mvnw spring-boot:run
```

The backend starts on **http://localhost:8080**.
On first run, Flyway migrations create the SQLite database at `./data/ops_console.db`
and the `DataSeeder` imports all five seed files automatically.

### 2. Run the frontend (separate terminal)

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:5173**. API calls are proxied to the backend.

### Run meaningful tests

```bash
cd backend
./mvnw test
```

---

## Demo Login Credentials

| Username    | Password    | Role         | Accessible clients          |
|-------------|-------------|--------------|----------------------------|
| `opsleader` | `ops123`    | OPS_LEAD     | All clients (CLI-001…004)  |
| `invest1`   | `invest123` | INVESTIGATOR | CLI-001, CLI-002, CLI-003   |
| `support1`  | `support123`| SUPPORT      | CLI-001, CLI-002 (masked)   |
| `auditor1`  | `audit123`  | AUDITOR      | All clients (read-only)    |

---

## What the Seed Data Demonstrates

After startup, the following cases are auto-detected:

| Case | Client | ISIN | Type | Severity | Why |
|------|--------|------|------|----------|-----|
| Holding mismatch | CLI-001 | INE040A01034 (HDFCBANK) | HOLDING_MISMATCH | HIGH | Internal=200, DP=180, Δ=+20 |
| Holding mismatch | CLI-002 | INE062A01020 (SBIN) | HOLDING_MISMATCH | HIGH | Internal=100, DP=120, Δ=−20 |
| Pending DP movement | CLI-001 | INE467B01029 (WIPRO) | PENDING_DP | MEDIUM | DP has 20 units PENDING; settled qty also mismatches |
| Missing DP source | CLI-004 | INE040A01034 (HDFCBANK) | MISSING_SOURCE | MEDIUM | No DP row for this (client, ISIN, cut) — UNKNOWN, not zero |
| Duplicate UTR | CLI-004 | — | CASH_RECONCILIATION | MEDIUM | UTR2024091610 appears twice — CONFLICTING_EVIDENCE |

---

## Project Structure

```
indiraSecurity/
├── backend/                        # Spring Boot 3 + Java 21
│   ├── src/main/java/com/indira/opsconsole/
│   │   ├── OpsConsoleApplication.java
│   │   ├── config/                 # JacksonConfig, IngestConfig
│   │   ├── controller/             # AuthController, ImportController, CaseController, MetricsController
│   │   │   └── dto/                # Response DTOs
│   │   ├── detection/              # CaseDetectionService, CaseFactory, CaseWorkflowService, MetricsService
│   │   ├── domain/
│   │   │   ├── entity/             # All JPA entities
│   │   │   └── enums/              # UserRole, CaseState, CaseSeverity, etc.
│   │   ├── ingest/                 # IngestService, RowPersistenceService, HashUtil, DataSeeder
│   │   │   └── adapter/            # StreamAdapter interface + 5 adapters + AdapterRegistry
│   │   ├── repository/             # Spring Data JPA repositories
│   │   └── security/               # JwtService, JwtAuthFilter, SecurityConfig, AuthHelper
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/migration/           # V1–V6 Flyway SQL
│       └── seed-data/              # 5 synthetic stream files
│
├── frontend/                       # React 18 + Vite 5 + MUI 5 + TypeScript
│   └── src/
│       ├── App.tsx, AuthContext.tsx, api.ts, types.ts
│       ├── pages/                  # LoginPage, DashboardPage, CaseListPage, CaseDetailPage, ImportsPage
│       └── components/             # Layout, SeverityChip, StateChip, UnknownBadge
│
├── seed-data/                      # Source seed files (also copied to classpath)
├── decisions.md                    # Data flow, assumptions, UNKNOWN cases, clarification questions
├── AI_USAGE.md                     # AI assistance log
└── README.md
```

---

## API Reference

```
POST /api/auth/login                  Public — returns JWT
POST /api/imports                     OPS_LEAD: ingest file (multipart/form-data)
GET  /api/imports                     INVESTIGATOR+: list all imports
GET  /api/imports/:id                 INVESTIGATOR+: import detail
GET  /api/cases?severity=&state=&...  All roles: paginated, server-scoped
GET  /api/cases/:id                   All roles: case detail + evidence pane
POST /api/cases/:id/notes             INVESTIGATOR, OPS_LEAD: add note
POST /api/cases/:id/transition        INVESTIGATOR, OPS_LEAD: state change (optimistic lock)
GET  /api/metrics                     All roles: queue health + freshness
GET  /actuator/health                 Public
```

---

## Key Design Decisions

1. **ISIN-only joins** — display symbol is metadata, never a join key
2. **Integer paise** — no floats anywhere in the money path
3. **Pending ≠ Settled** — PENDING DP rows go to `dp_pending_movements`, never to holdings
4. **Idempotent import** — SHA-256 unique constraint; same bytes = no-op
5. **Optimistic locking** — `@Version` on `cases` table; 409 on concurrent transition conflict
6. **SUPPORT masking at API layer** — masked client IDs and no raw JSON in serialized responses
7. **Notes survive file corrections** — evidence links point to frozen `source_file_id` snapshots

---

## Docker Compose (optional)

```bash
docker compose up --build
```

See `docker-compose.yml` in project root.

---

## Known Limitations

- Live NSE/BSE/SEBI portal adapters are scaffolded; offline CSV/HTML files represent those sources for the demo.
- Performance benchmarks (100k ledger rows, 10k holdings, p95 < 300ms) are not yet measured. A reproducible seed generator is in `DataSeeder`.
- PostgreSQL migration: change `spring.datasource.url` and add `flyway-postgres` dependency; all SQL is ANSI-compatible except SQLite dialect config.
