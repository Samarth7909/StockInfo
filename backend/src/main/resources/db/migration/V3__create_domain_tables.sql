-- Normalized holdings (internal broker snapshot)
CREATE TABLE IF NOT EXISTS normalized_holdings (
    id               TEXT PRIMARY KEY,
    source_file_id   TEXT NOT NULL REFERENCES source_files(id),
    raw_row_id       TEXT REFERENCES raw_rows(id),
    client_id        TEXT NOT NULL,
    isin             TEXT NOT NULL,
    position_type    TEXT NOT NULL,   -- 'EQUITY', 'MUTUAL_FUND', etc.
    cut_at           TIMESTAMP NOT NULL,
    settled_qty      BIGINT NOT NULL,
    exchange_symbol  TEXT,
    series           TEXT,
    data_source      TEXT NOT NULL    -- 'INTERNAL' or 'DP'
);

CREATE INDEX IF NOT EXISTS idx_holdings_key ON normalized_holdings(client_id, isin, position_type, cut_at, data_source);

-- DP pending movements (kept separate from settled)
CREATE TABLE IF NOT EXISTS dp_pending_movements (
    id               TEXT PRIMARY KEY,
    source_file_id   TEXT NOT NULL REFERENCES source_files(id),
    raw_row_id       TEXT REFERENCES raw_rows(id),
    client_id        TEXT NOT NULL,
    isin             TEXT NOT NULL,
    position_type    TEXT NOT NULL,
    cut_at           TIMESTAMP NOT NULL,
    pending_qty      BIGINT NOT NULL,
    movement_type    TEXT,
    exchange_symbol  TEXT
);

CREATE INDEX IF NOT EXISTS idx_dp_pending_key ON dp_pending_movements(client_id, isin, cut_at);

-- Cash events (cash ledger)
CREATE TABLE IF NOT EXISTS cash_events (
    id               TEXT PRIMARY KEY,
    source_file_id   TEXT NOT NULL REFERENCES source_files(id),
    raw_row_id       TEXT REFERENCES raw_rows(id),
    client_id        TEXT NOT NULL,
    event_id         TEXT,            -- broker's own event identifier
    amount_paise     BIGINT NOT NULL, -- signed integer; negative = debit
    state            TEXT NOT NULL CHECK(state IN ('POSTED','PENDING')),
    effective_at     TIMESTAMP NOT NULL,
    narration        TEXT
);

CREATE INDEX IF NOT EXISTS idx_cash_events_client ON cash_events(client_id, state, effective_at);

-- Bank confirmation entries
CREATE TABLE IF NOT EXISTS bank_entries (
    id               TEXT PRIMARY KEY,
    source_file_id   TEXT NOT NULL REFERENCES source_files(id),
    raw_row_id       TEXT REFERENCES raw_rows(id),
    client_id        TEXT,
    utr_reference    TEXT,
    direction        TEXT CHECK(direction IN ('CREDIT','DEBIT')),
    amount_paise     BIGINT NOT NULL,
    bank_status      TEXT,
    value_date       DATE,
    narration        TEXT,
    human_matched    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_bank_entries_utr ON bank_entries(utr_reference);

-- Exchange reference (symbol/ISIN/series)
CREATE TABLE IF NOT EXISTS exchange_reference (
    id                   TEXT PRIMARY KEY,
    source_file_id       TEXT NOT NULL REFERENCES source_files(id),
    raw_row_id           TEXT REFERENCES raw_rows(id),
    isin                 TEXT NOT NULL,
    symbol               TEXT,
    series               TEXT,
    exchange             TEXT,       -- 'NSE' or 'BSE'
    reference_price_paise BIGINT,
    effective_date      DATE
);

CREATE INDEX IF NOT EXISTS idx_exchange_ref_isin ON exchange_reference(isin);
