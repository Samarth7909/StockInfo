-- Exception cases
CREATE TABLE IF NOT EXISTS cases (
    id                   TEXT PRIMARY KEY,
    case_type            TEXT NOT NULL,   -- 'HOLDING_MISMATCH','PENDING_DP','CASH_RECONCILIATION','MISSING_SOURCE','UNMATCHED_IDENTITY'
    severity             TEXT NOT NULL CHECK(severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    state                TEXT NOT NULL DEFAULT 'OPEN'
                           CHECK(state IN ('OPEN','INVESTIGATING','NEEDS_SOURCE','RESOLVED','REOPENED')),
    client_id            TEXT NOT NULL,
    isin                 TEXT,
    cut_at                 TIMESTAMP,
    quantity_delta       BIGINT,          -- nonzero = mismatch; null for cash cases
    amount_delta_paise   BIGINT,          -- for cash cases; null for holding cases
    evidence_state       TEXT NOT NULL DEFAULT 'UNKNOWN',
                                          -- 'MATCHED','UNMATCHED_IDENTITY','STALE_CUT','CONFLICTING_EVIDENCE','MISSING_SOURCE'
    description          TEXT,
    version              INTEGER NOT NULL DEFAULT 1,  -- optimistic lock
    created_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    updated_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_cases_client ON cases(client_id);
CREATE INDEX IF NOT EXISTS idx_cases_severity ON cases(severity);
CREATE INDEX IF NOT EXISTS idx_cases_state ON cases(state);
CREATE INDEX IF NOT EXISTS idx_cases_cut ON cases(cut_at);

-- Evidence links: case → source row + version
CREATE TABLE IF NOT EXISTS case_evidence (
    id               TEXT PRIMARY KEY,
    case_id          TEXT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    source_file_id   TEXT NOT NULL REFERENCES source_files(id),
    raw_row_id       TEXT REFERENCES raw_rows(id),
    mapping_version  TEXT NOT NULL DEFAULT 'v1',
    evidence_role    TEXT NOT NULL,    -- 'INTERNAL_HOLDING','DP_HOLDING','CASH_EVENT','BANK_ENTRY','EXCHANGE_REF'
    added_at         TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_case_evidence_case ON case_evidence(case_id);

-- Case notes (investigator annotations)
CREATE TABLE IF NOT EXISTS case_notes (
    id                        TEXT PRIMARY KEY,
    case_id                   TEXT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    author_user_id            TEXT NOT NULL REFERENCES users(id),
    body                      TEXT NOT NULL,
    evidence_version_snapshot TEXT,   -- JSON snapshot of evidence file IDs at time of note
    created_at                TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_case_notes_case ON case_notes(case_id);

-- State transition audit log
CREATE TABLE IF NOT EXISTS case_transitions (
    id               TEXT PRIMARY KEY,
    case_id          TEXT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    from_state       TEXT NOT NULL,
    to_state         TEXT NOT NULL,
    reason           TEXT NOT NULL,
    user_id          TEXT NOT NULL REFERENCES users(id),
    evidence_version INTEGER NOT NULL,
    changed_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_case_transitions_case ON case_transitions(case_id);
