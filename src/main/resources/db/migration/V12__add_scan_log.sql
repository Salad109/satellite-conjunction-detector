CREATE TABLE scan_log
(
    id                    SERIAL PRIMARY KEY,
    started_at            TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at          TIMESTAMP WITH TIME ZONE,
    duration_ms           BIGINT                   DEFAULT 0,
    satellites_scanned    INTEGER                  DEFAULT 0,
    conjunctions_detected INTEGER                  DEFAULT 0
);

CREATE INDEX idx_scan_log_started_at ON scan_log (started_at);
