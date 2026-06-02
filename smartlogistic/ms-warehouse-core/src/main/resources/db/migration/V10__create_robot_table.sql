-- ============================================================
-- V10: Create robot table for persistent robot registry
-- Stores base robot info that ms-robot-status caches in Redis
-- ============================================================

CREATE TABLE robot (
    id               VARCHAR(20) PRIMARY KEY,
    name             VARCHAR(50) NOT NULL,
    battery_level    INT         NOT NULL DEFAULT 100,
    available        BOOLEAN     NOT NULL DEFAULT true,
    current_location VARCHAR(30),
    operational_mode VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Seed the 5 warehouse robots (matching DataInitializer in ms-robot-status)
-- Locations reference V8 root_point codes
INSERT INTO robot (id, name, battery_level, available, current_location, operational_mode) VALUES
    ('RBT-01',  'Alpha',   85, true,  'RP-R00-C00', 'IDLE'),       -- START/entry
    ('RBT-02',  'Beta',    72, true,  'RP-R01-C05', 'IDLE'),       -- Main corridor
    ('RBT-03',  'Gamma',   45, true,  'RP-R03-C05', 'IDLE'),       -- Middle corridor
    ('RBT-LOW', 'Delta',   10, true,  'RP-R04-C00', 'CHARGING'),   -- Charging station
    ('RBT-MID', 'Epsilon', 12, false, 'RP-R05-C09', 'ERROR');      -- Charging station (maintenance)