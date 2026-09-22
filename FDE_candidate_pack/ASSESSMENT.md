# Stock broking FDE assessment: Operations Exception Desk

**Candidate instructions · Version 1.0 · 16 September 2026**

## Your assignment

Build a **read-only operations console** for an Indian stock broker. At the start of each working day, operations staff need to answer: *Which client account exceptions need attention, what evidence supports each exception, who may see it, and what changed when a source file was corrected?* The application ingests reports from several sources, normalizes them, identifies discrepancies, and lets authorized users investigate and annotate cases. It never trades.

Use the synthetic source bundle supplied with this brief. Do not use real client records, broker credentials, trading accounts, or live market order APIs. All money and holdings in the bundle are fictional. There is **no order placement, order modification, cancellation, execution, or trading recommendation** anywhere in scope. Read-only historical market and regulatory reference material is allowed.

This is one project with cumulative levels. Start at Level 1 and proceed as far as you can. A working lower level with honest limitations is preferable to a polished screen that invents a financial result. You may use ChatGPT, Claude, Copilot, or other tools. In the review, you must explain and modify your own implementation without outsourcing the live exercise. State which parts you used AI to draft or debug.

### What the desk actually receives

The starter bundle has five independent streams plus `access_scopes.json`, a synthetic map of client ownership and demo users:

| Stream | Delivery | What it represents | Important caveat |
| --- | --- | --- | --- |
| Internal holdings snapshot | CSV | What the broker's client view showed at a particular cut | A snapshot is not a depository statement. |
| DP position extract | HTML table | What a fictional depository export shows, including a pending movement | A pending movement must not silently become a settled holding. |
| Internal cash ledger | JSON lines | Posted and pending cash events | An event's `amount_paise` is signed; a `PENDING` event is not posted cash. |
| Bank confirmation | XLSX | External credit/debit reference and status | A bank reference is not a unique ledger event ID. |
| Exchange reference | CSV | Fictional symbol/ISIN/series mapping and prices | Price is a reference, not evidence of account entitlement. |

The interviewer may hand over a corrected version, a late source, or a changed column heading during the review. Preserve both versions and their provenance. Do not fetch private broker or DP portals; the supplied local exports represent these protected sources.

### Ingest a public portal safely

Acquire one read-only artifact or page from **each** of the following public portals, record its provenance, and implement a reusable adapter for at least one of them. Manual download/import is a valid acquisition path:

1. **NSE All Reports**: locate an equity report or historical report and record its URL, report date, download time, file name, content hash, and column mapping. NSE lists common bhavcopy reports, including UDiFF variants. Do not assume a fixed legacy schema.
2. **BSE Bhav Copy / Market Data Products**: locate an EOD report, record the same provenance, and show how its identifiers and headings differ from your internal reference stream. Do not join exchanges solely by display symbol.
3. **SEBI Circulars**: select one circular relevant to a stock broker's operations, record publication date, circular identifier, scope, URL, and a short human verified impact note. A circular is an evidence link, not a rule engine input until a reviewer has approved a versioned rule.

If automated access is blocked, rate limited, or prohibited by site terms, use a **manual download/import** for that source and document why. If even manual access is unavailable during your time window, record the URL, failed attempt and limitation without fabricating an artifact. Never bypass a login, CAPTCHA, bot challenge, or access control. The app must remain testable from the supplied offline bundle. A current public document can change after this assessment was prepared; do not invent an exact circular number or claim a regulatory rule from a summary alone.

Official entry points: [NSE All Reports](https://www.nseindia.com/all-reports), [BSE Bhav Copy](https://www.bseindia.com/markets/marketinfo/bhavcopy), [SEBI Circulars](https://www.sebi.gov.in/sebiweb/home/HomeAction.do?doListing=yes&sid=1&smid=0&ssid=7).

## Domain rules for this exercise

These rules are **assessment rules for synthetic data**, not claims about real settlement, reporting, or regulatory deadlines.

1. Identify a holding by `(client_id, ISIN, position_type, cut_at)`; preserve exchange symbol, series, and the original row. An unmatched symbol is an exception, never a guessed match. A DP row with `movement_state=PENDING` is displayed separately from settled quantity.
2. Compare only compatible snapshots at the same cut and position type. For a settled holding, `quantity_delta = internal_settled_qty - dp_settled_qty`. A zero is a match. Any nonzero delta is an exception, even if a pending DP movement of the same size exists; pending evidence can inform the explanation but cannot erase it.
3. Posted cash at a cut is the exact integer sum of `amount_paise` for events with `state=POSTED` and `effective_at <= cut_at`. Compute money in integer paise or exact decimals, never binary floating point. A later event is not retroactively posted at an earlier cut.
4. A bank entry can support a cash case only when a human or a documented matching rule has checked client, direction, amount, reference, and timing. Duplicated references are ambiguous. Keep the source references; do not automatically net a posted ledger entry against a bank row when its identity is uncertain.
5. One source's silence is **unknown**, not zero. `MISSING_SOURCE`, `UNMATCHED_IDENTITY`, `STALE_CUT`, and `CONFLICTING_EVIDENCE` are first class states. Unknown or stale data cannot be labelled `MATCHED`.
6. A corrected file supersedes a prior file only for a specified stream and cut, with an audit trail. The import operation is idempotent for identical bytes; changed bytes under the same filename create a new version and require explicit comparison. Do not destroy a prior investigator note.
7. Severity is based on case impact and evidence, **not on the color of a market move**. `CRITICAL`: wrong client scope, access leak, corrupted source, or untrustworthy aggregate. `HIGH`: confirmed nonzero settled holding or posted cash mismatch at a comparable cut. `MEDIUM`: stale, missing, or ambiguous evidence that prevents a reliable comparison. `LOW`: informational item without a financial discrepancy. A critical permission leak blocks release, whatever the case count.

## Levels and deliverables

| Level | Work to demonstrate | Minimum observable result |
| --- | --- | --- |
| 0: Desk readback (15 min) | Draw the data flow, write five clarification questions, identify source authority and two cases that must stay `UNKNOWN`. | One page `decisions.md`, with explicit assumptions. |
| 1: Working slice (roughly 2 h) | Parse the five offline streams; normalize identity, quantities and paise; persist source versions; expose read-only case list and detail API; build a basic UI with filters and a case detail evidence pane. | A reproducible import and a visible settled holding mismatch, ambiguous cash item, and pending DP item; provenance on each. |
| 2: Operations workflow (1–2 h more) | Login or a documented local demo identity; server side RBAC; case notes and status with audit; severity; pagination; validation errors; import status; retries; responsive UI and clear missing/stale states. | Support user sees only permitted masked clients. Investigator can add a note. An unauthorized request returns a denial regardless of UI state. |
| 3: Reliability (1–2 h more) | Design adapters behind a shared interface or equivalent OOP abstraction; alternate mappings; idempotent import; concurrent import of the same report; corrected file versioning; pagination under load; operational metrics. | Repeat import changes no cases. Changed bytes produce a version comparison. Concurrent retries do not double count. |
| 4: Senior FDE review (live 45–60 min) | Respond to a new evidence packet and a policy change, analyze an access defect, profile a slow query, propose a scale plan and an incident response. | Make a small working change, explain an unresolved ambiguity, and show a measurement or a safe test. |

Suggested baseline time: **4 hours** for Levels 0–2 plus an optional **up to 3 hours** for Level 3; the interviewer runs Level 4 live. Freshers are expected to demonstrate the working slice and reasoning, not to finish Level 4. If time runs out, submit the current state. You can choose any ordinary web stack and database, including SQLite for a local demo. Include one command to start and one command to run your meaningful tests. No hosted deployment is required.

### Case workflow

Use states `OPEN`, `INVESTIGATING`, `NEEDS_SOURCE`, `RESOLVED`, and `REOPENED`. Store who changed state, when, why, and which evidence version they relied on. A note may say “pending DP movement might explain delta”; it must not rewrite source data. Only an investigator with access to that client may add a note; only an operations lead may resolve a financial discrepancy; an auditor can read evidence and history but not edit. Reopen a resolved case if relevant evidence changes. A support user sees a masked summary, not full account identifiers, raw bank narration, or internal import controls. Enforce these checks in APIs and any exports, not just in buttons.

### API / UI contract

Your route names may differ, but show equivalents of:

```text
POST /imports                     synthetic/public read-only file ingestion; role: ops lead
GET  /imports/:id                 state, source, cut, hash, schema, row counts, errors
GET  /cases?severity=&state=&...  paginated, server filtered by user scope
GET  /cases/:id                   normalized result plus source row and version references
POST /cases/:id/notes             audit stamped note, role: investigator or ops lead
POST /cases/:id/transition        reason, expected prior state/version, role checked
GET  /metrics                     queue health, source freshness, import and query timing
```

Show source name, report cut, received time, file hash, raw row identifier, and mapping version in a case's evidence panel. Show a distinct **unknown** result when a source is absent. A dashboard summary must have definitions for its counts. The UI should remain usable by keyboard and on a laptop screen; use loading, error, empty, and restricted states.

### Conditions the code must survive

- `ISIN` joins across streams; a display symbol can be renamed, reused, or differ across venues. Exchange + series is contextual information, not a universal security key.
- Spaces, lowercase, different date spellings, paise versus rupees, commas, nullable fields, and column reorder must be handled or explicitly quarantined with row errors. Do not guess currency unit from magnitude.
- A source can be late or republished. Imports can arrive out of order. A report cut and the receipt timestamp are distinct.
- Two workers can ingest the same file while an investigator edits a note. Make import idempotency and note preservation testable. Define your conflict behavior for two simultaneous state transitions.
- Local performance target for a seeded **100,000 ledger rows, 10,000 holdings, 2,000 concurrent simulated readers**: use a measured workload and state the hardware, database, warm/cold condition, and p50/p95. If you cannot generate this load in time, provide a reproducible generator and bottleneck analysis. Do **not** claim an unmeasured p95. Aim for p95 under 300 ms for paginated case list with 50 rows on the evaluator's local machine; document if unattained. The target is an interview benchmark, not a production SLA.
- Explain how you would operate 50 desks and several million historical events without cross desk data leaks. Address queueing, backpressure, indexes, source retention, and failure isolation before suggesting extra services.

## Short desk messages to interpret

These are intentionally like real operations notes. They are **questions to investigate**, not technical specifications or hidden magic words. Record what you checked before you act.

> “The green tile is showing, but the DP file landed after the cut. Can we close it?”

> “This ISIN is on both screens, though the scrip label changed. Which account is short?”

> “A bank UTR repeats in the feed. The client says the money came once. Keep both lines?”

> “Yesterday's export was corrected this morning. The note on the old case must still be there.”

A strong solution explains exactly which evidence would answer each question. You will not be scored for guessing a broker's undocumented jargon. Ask the interviewer if a desk phrase could alter a financial outcome.

## Submission and demonstration

Submit source, migration/seed scripts, `README.md`, `decisions.md`, tests, screenshots or a local demo, and a short `AI_USAGE.md` naming what AI helped with and what you verified yourself. Include a sample import with expected case IDs and a list of unresolved data quality problems. Demonstrate one support and one investigator session, a direct denied API call, an idempotent reimport, and one case whose status does not auto resolve from pending evidence.

At review, expect to trace one input row to one case, fix a changed field or late report while screen sharing, defend your normalization and authorization boundaries, and say “insufficient evidence” when appropriate. We assess working behavior, traceability, calm investigation, and the ability to revise a decision. AI assistance is permitted; copying an unverified AI explanation is not evidence.
