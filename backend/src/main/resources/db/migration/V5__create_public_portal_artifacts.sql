-- Public portal artifact provenance (NSE, BSE, SEBI)
CREATE TABLE IF NOT EXISTS public_portal_artifacts (
    id               TEXT PRIMARY KEY,
    portal           TEXT NOT NULL CHECK(portal IN ('NSE','BSE','SEBI')),
    artifact_type    TEXT NOT NULL,   -- 'BHAVCOPY','EQUITY_REPORT','CIRCULAR'
    report_date      TEXT,
    download_time    TEXT NOT NULL,
    filename         TEXT,
    source_url       TEXT,
    sha256_hash      TEXT,
    column_mapping   TEXT,            -- JSON
    notes            TEXT,
    acquisition_path TEXT NOT NULL DEFAULT 'MANUAL'  -- 'MANUAL' or 'AUTOMATED'
);

-- SEBI circulars (evidence links only, not rule engine inputs)
CREATE TABLE IF NOT EXISTS sebi_circulars (
    id               TEXT PRIMARY KEY,
    circular_id      TEXT NOT NULL,
    publication_date TEXT NOT NULL,
    scope            TEXT,
    source_url       TEXT NOT NULL,
    impact_note      TEXT,            -- human-verified
    human_verified   INTEGER NOT NULL DEFAULT 0,
    artifact_id      TEXT REFERENCES public_portal_artifacts(id),
    created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);
