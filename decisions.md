# decisions.md — Operations Exception Desk
**Version 1.0 · September 2026**

---

## Data Flow Diagram

```
[Seed / Upload files]
        │
        ▼
[IngestService]
  ├─ SHA-256 hash check ──► [Identical hash] ──► No-op (200 OK, idempotent)
  │
  ├─ Persist SourceFile (status=PENDING)
  ├─ Dispatch to StreamAdapter (parse + normalise)
  ├─ Persist RawRows (immutable JSON snapshots)
  ├─ Persist domain rows (holdings / cash / bank / exchange)
  ├─ Update SourceFile (status=DONE or ERROR)
  └─ Trigger CaseDetectionService
              │
              ▼
      [Case Detection Rules]
        Rule 1: HOLDING_MISMATCH  — internal settled ≠ DP settled at same (client,ISIN,posType,cut)
        Rule 2: PENDING_DP        — DP rows with movement_state=PENDING
        Rule 3: CASH_RECONCILIATION — duplicate UTR in bank_entries
        Rule 4: MISSING_SOURCE    — internal holding has no matching DP row for the cut
              │
              ▼
      [ExceptionCase table]
        ├─ New case → state=OPEN
        ├─ Re-run on same cut → update severity/delta in-place (notes preserved)
        └─ RESOLVED case → reopen if delta changed
              │
              ▼
[REST API — role-filtered]
  GET  /api/cases          — paginated, scoped to user's accessible_client_ids
  GET  /api/cases/:id      — full evidence pane (raw JSON hidden for SUPPORT)
  POST /api/cases/:id/notes       — INVESTIGATOR / OPS_LEAD only
  POST /api/cases/:id/transition  — optimistic lock on version field
  POST /api/imports        — OPS_LEAD only
  GET  /api/metrics        — all authenticated roles
              │
              ▼
[React Frontend]
  LoginPage → Dashboard → CaseList → CaseDetail → EvidencePane / Notes / Transitions
```

---

## Key Assumptions

| # | Assumption | Rationale |
|---|-----------|-----------|
| 1 | **ISIN is the only universal security key** | Display symbol can be renamed, reused, or differ across NSE/BSE. Never join on symbol. |
| 2 | **All money is stored as signed integers in paise** | Binary floats cannot represent exact monetary values. 1 INR = 100 paise. |
| 3 | **PENDING DP movements are never merged into settled quantity** | A pending movement may explain a delta but cannot erase it. |
| 4 | **Missing source = UNKNOWN, not zero** | Silence from a stream is not evidence of zero. Cannot label MATCHED. |
| 5 | **SHA-256 hash gates all re-ingestion** | Same bytes → same hash → no-op. Changed bytes under same filename → new version. |
| 6 | **Concurrent state transitions use optimistic locking** | Caller supplies expected `version`. Second writer gets HTTP 409 Conflict. No last-write-wins. |
| 7 | **Bank UTR duplicates are CONFLICTING_EVIDENCE** | A UTR repeat is ambiguous. Human (or documented matching rule) must verify before matching. |
| 8 | **Only OPS_LEAD may RESOLVE a financial discrepancy** | Operations workflow rule: support and investigators may investigate, not close. |
| 9 | **SUPPORT role is enforced at API query time** | Masked client IDs and hidden raw rows are not just UI — they are omitted from API responses. |
| 10 | **DataSeeder runs on every startup; it is idempotent** | `INSERT OR IGNORE` + hash check means safe to re-run. |

---

## Two Cases That Must Stay UNKNOWN

### Case A — Missing DP cut
**Scenario**: CLI-004 has HDFCBANK (INE040A01034) in the internal holdings snapshot
but the DP extract has no row for this (client, ISIN, cut) combination.

**Why it cannot be MATCHED**: The absence of a DP row is not the same as a DP position of zero.
The DP file may not have been received yet, may be stale, or may cover a different population.
Evidence state = `MISSING_SOURCE`. Human investigation required before any conclusion.

### Case B — Duplicate bank UTR
**Scenario**: UTR reference `UTR2024091610` appears twice in the bank confirmation feed,
both rows for CLI-004, same amount (30,000,000 paise), both PENDING.

**Why it cannot be MATCHED**: We cannot determine whether this represents one payment
(duplicate feed line) or two separate payments of the same amount. The client states the money
came once, but the feed shows two lines. Both lines are preserved. The case state is
`CONFLICTING_EVIDENCE`. Matching requires: human verification of client, direction,
amount, reference, timing — and explicit `humanMatched = true` flag on the confirmed row.

---

## Five Clarification Questions

1. **Cut boundary**: Is the internal holdings snapshot always taken at exactly 18:00 on the trade date, or can it be intraday? If intraday cuts exist, two snapshots of the same date may be incomparable.

2. **DP pending scope**: Should a PENDING DP movement of the exact same size as the delta be shown as an *explanation* in the case description, or should it suppress the mismatch case entirely? (Current rule: it explains but does not erase.)

3. **Bank UTR uniqueness policy**: Does the broker's bank mandate globally unique UTR references, or can the same UTR appear in multiple remittances? This changes whether a duplicate UTR is always CONFLICTING_EVIDENCE or only sometimes.

4. **Corrected file scope**: When a corrected file is provided, does it replace the entire cut or only certain rows? If partial, which rows are authoritative?

5. **Exchange symbol rename policy**: When a scrip's display symbol changes (e.g., INFOSYS → INFY), is the historical data backfilled with the new symbol, or do old records keep the old symbol? This affects how the evidence pane describes the same ISIN across two time periods.

---

## Unresolved Data Quality Problems

- `bank_confirmation.csv` row 11 and row 10 share UTR `UTR2024091610` — ambiguous, preserved as-is.
- `exchange_reference.csv` contains `INE123X01234` (UNKNOWN_CO) which has no matching ISIN in any holdings file. This is an unmatched symbol, treated as an orphan exchange reference row.
- DP extract has no row for CLI-004 / HDFCBANK at the 2024-09-16 cut — MISSING_SOURCE case raised.
