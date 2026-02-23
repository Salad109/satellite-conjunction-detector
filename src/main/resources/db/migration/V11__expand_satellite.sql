-- New
ALTER TABLE satellite
    ADD COLUMN object_id VARCHAR(12),
    ADD COLUMN classification_type VARCHAR(1),
    ADD COLUMN country_code       VARCHAR(6),
    ADD COLUMN launch_date        DATE,
    ADD COLUMN site               VARCHAR(5),
    ADD COLUMN decay_date         DATE,
    ADD COLUMN creation_date      TIMESTAMP,
    ADD COLUMN tle_line0          VARCHAR(27),
    ADD COLUMN mean_motion_dot    NUMERIC(9,8),
    ADD COLUMN mean_motion_ddot   NUMERIC(22,13),
    ADD COLUMN mean_anomaly       NUMERIC(7,4),
    ADD COLUMN ephemeris_type     INTEGER,
    ADD COLUMN bstar              NUMERIC(19,14),
    ADD COLUMN rcs_size           VARCHAR(6),
    ADD COLUMN element_set_no     INTEGER,
    ADD COLUMN rev_at_epoch       INTEGER,
    ADD COLUMN period             DOUBLE PRECISION,
    ADD COLUMN file_number        BIGINT,
    ADD COLUMN gp_id              INTEGER;

-- Fix existing columns to match GP spec
ALTER TABLE satellite
ALTER
COLUMN mean_motion TYPE NUMERIC(13,8),
    ALTER
COLUMN eccentricity TYPE NUMERIC(13,8),
    ALTER
COLUMN inclination TYPE NUMERIC(7,4),
    ALTER
COLUMN raan TYPE NUMERIC(7,4),
    ALTER
COLUMN arg_perigee TYPE NUMERIC(7,4);
