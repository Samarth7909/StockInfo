-- Source file versioning (one record per ingested file)
CREATE TABLE IF NOT EXISTS source_files (
    id               TEXT PRIMARY KEY,
    stream_name      TEXT NOT NULL,
    cut_at           TIMESTAMP,               -- nullable if not determinable
    received_at      TIMESTAMP NOT NULL,
    filename         TEXT NOT NULL,
    sha256_hash      TEXT NOT NULL,
    schema_version   TEXT NOT NULL DEFAULT 'v1',
    row_count        INTEGER NOT NULL DEFAULT 0,
    error_count      INTEGER NOT NULL DEFAULT 0,
    status           TEXT NOT NULL DEFAULT 'PENDING'
                       CHECK(status IN ('PENDING','PROCESSING','DONE','ERROR')),
    error_detail     TEXT,
    imported_by      TEXT REFERENCES users(id),
    mapping_version  TEXT NOT NULL DEFAULT 'v1'
);

-- Unique: same hash for the same stream is idempotent
CREATE UNIQUE INDEX IF NOT EXISTS uq_source_files_hash ON source_files(stream_name, sha256_hash);

-- Raw rows preserved per import version
CREATE TABLE IF NOT EXISTS raw_rows (
    id               TEXT PRIMARY KEY,
    source_file_id   TEXT NOT NULL REFERENCES source_files(id) ON DELETE CASCADE,
    row_index        INTEGER NOT NULL,
    raw_json         TEXT NOT NULL   -- original row as JSON
);

CREATE INDEX IF NOT EXISTS idx_raw_rows_source ON raw_rows(source_file_id);
