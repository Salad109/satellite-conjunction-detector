ALTER TABLE satellite
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
DROP INDEX IF EXISTS idx_satellite_inclination;
DROP INDEX IF EXISTS idx_satellite_orbital_shell;