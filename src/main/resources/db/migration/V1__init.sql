-- Satellite catalog with current TLE data (simplified - no history)

CREATE TABLE satellite
(
    norad_cat_id       INTEGER PRIMARY KEY,
    object_name        VARCHAR(25),
    object_type        VARCHAR(20),      -- PAYLOAD, ROCKET BODY, DEBRIS, UNKNOWN
    country_code       VARCHAR(10),
    launch_date        DATE,
    decay_date         DATE,

    -- Current TLE data
    epoch              TIMESTAMP WITH TIME ZONE,
    tle_line1          VARCHAR(70),
    tle_line2          VARCHAR(70),

    -- Orbital elements
    mean_motion        DOUBLE PRECISION, -- revs/day
    eccentricity       DOUBLE PRECISION, -- 0-1
    inclination        DOUBLE PRECISION, -- degrees
    raan               DOUBLE PRECISION, -- right ascension of ascending node, degrees
    arg_perigee        DOUBLE PRECISION, -- argument of perigee, degrees
    mean_anomaly       DOUBLE PRECISION, -- degrees
    bstar              DOUBLE PRECISION, -- drag term

    -- Derived values for fast filtering
    semi_major_axis_km DOUBLE PRECISION,
    perigee_km         DOUBLE PRECISION,
    apogee_km          DOUBLE PRECISION,
    orbital_period_min DOUBLE PRECISION,

    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for orbital shell filtering
CREATE INDEX idx_satellite_orbital_shell ON satellite (perigee_km, apogee_km);
CREATE INDEX idx_satellite_inclination ON satellite (inclination);

-- Conjunction candidates table (for caching screening results)
CREATE TABLE conjunction_candidate
(
    id                     BIGSERIAL PRIMARY KEY,
    object1_norad_id       INTEGER                  NOT NULL REFERENCES satellite (norad_cat_id),
    object2_norad_id       INTEGER                  NOT NULL REFERENCES satellite (norad_cat_id),
    tca                    TIMESTAMP WITH TIME ZONE NOT NULL, -- time of closest approach
    miss_distance_km       DOUBLE PRECISION         NOT NULL,
    relative_velocity_km_s DOUBLE PRECISION,
    collision_probability  DOUBLE PRECISION,
    screening_timestamp    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT objects_ordered CHECK (object1_norad_id < object2_norad_id)
);

CREATE INDEX idx_conjunction_tca ON conjunction_candidate (tca);
CREATE INDEX idx_conjunction_distance ON conjunction_candidate (miss_distance_km);

-- Ingestion log for tracking sync status
CREATE TABLE ingestion_log
(
    id                SERIAL PRIMARY KEY,
    started_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at      TIMESTAMP WITH TIME ZONE,
    objects_processed INTEGER                  DEFAULT 0,
    objects_inserted  INTEGER                  DEFAULT 0,
    objects_updated   INTEGER                  DEFAULT 0,
    status            VARCHAR(20)              DEFAULT 'RUNNING', -- RUNNING, COMPLETED, FAILED
    error_message     TEXT
);
