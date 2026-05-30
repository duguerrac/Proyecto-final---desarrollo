-- ============================================================
-- V8: Rewrite root_points + route_edges to match the grid layout
-- Grid: 10 cols × 6 rows, cellSize = 200 UE units (2 meters)
-- Coordinates: x = col * 200, y = row * 200
-- This ensures DB coordinates match UE5 CellToWorldPosition()
-- ============================================================

-- Clear old data (respect FK order)
DELETE FROM route_step WHERE route_plan_id IN (SELECT id FROM route_plan);
DELETE FROM route_plan;
DELETE FROM spot_item;
DELETE FROM spot;
DELETE FROM route_edge;
DELETE FROM root_point;
DELETE FROM order_item;
DELETE FROM dispatch_order;

-- Reset sequences
ALTER SEQUENCE IF EXISTS root_point_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS route_edge_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS spot_id_seq RESTART WITH 1;

-- ============================================================
-- Root Points (one per grid cell, matching UE5 layout)
-- Grid layout (row, col):
--   Row 0: ENTRY  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EXIT
--   Row 1: EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY
--   Row 2: EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  EMPTY
--   Row 3: EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY
--   Row 4: CHARGE EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY
--   Row 5: CHARGE EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  CHARGE
-- ============================================================

INSERT INTO root_point (code, x, y, z_level, type, blocked, max_speed) VALUES
-- Row 0: Top corridor (receiving dock → shelves → exit)
('RP-R00-C00',    0,    0, 1, 'START',    FALSE, 1.0),
('RP-R00-C01',  200,    0, 1, 'INTERNAL', FALSE, 1.0),
('RP-R00-C02',  400,    0, 1, 'INTERNAL', FALSE, 0.8),
('RP-R00-C03',  600,    0, 1, 'INTERNAL', FALSE, 1.0),
('RP-R00-C04',  800,    0, 1, 'INTERNAL', FALSE, 0.8),
('RP-R00-C05', 1000,    0, 1, 'INTERNAL', FALSE, 1.0),
('RP-R00-C06', 1200,    0, 1, 'INTERNAL', FALSE, 0.8),
('RP-R00-C07', 1400,    0, 1, 'INTERNAL', FALSE, 1.0),
('RP-R00-C08', 1600,    0, 1, 'INTERNAL', FALSE, 0.8),
('RP-R00-C09', 1800,    0, 1, 'EXIT',     FALSE, 1.0),

-- Row 1: Main corridor (full walkable)
('RP-R01-C00',    0,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C01',  200,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C02',  400,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C03',  600,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C04',  800,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C05', 1000,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C06', 1200,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C07', 1400,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C08', 1600,  200, 1, 'INTERNAL', FALSE, 1.0),
('RP-R01-C09', 1800,  200, 1, 'INTERNAL', FALSE, 1.0),

-- Row 2: Shelf row (shelves with corridor gaps)
('RP-R02-C00',    0,  400, 1, 'INTERNAL', FALSE, 1.0),
('RP-R02-C01',  200,  400, 1, 'INTERNAL', FALSE, 0.8),
('RP-R02-C02',  400,  400, 1, 'INTERNAL', FALSE, 1.0),
('RP-R02-C03',  600,  400, 1, 'INTERNAL', FALSE, 0.8),
('RP-R02-C04',  800,  400, 1, 'INTERNAL', FALSE, 1.0),
('RP-R02-C05', 1000,  400, 1, 'INTERNAL', FALSE, 0.8),
('RP-R02-C06', 1200,  400, 1, 'INTERNAL', FALSE, 1.0),
('RP-R02-C07', 1400,  400, 1, 'INTERNAL', FALSE, 0.8),
('RP-R02-C08', 1600,  400, 1, 'INTERNAL', FALSE, 1.0),
('RP-R02-C09', 1800,  400, 1, 'INTERNAL', FALSE, 1.0),

-- Row 3: Middle corridor (full walkable)
('RP-R03-C00',    0,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C01',  200,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C02',  400,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C03',  600,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C04',  800,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C05', 1000,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C06', 1200,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C07', 1400,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C08', 1600,  600, 1, 'INTERNAL', FALSE, 1.0),
('RP-R03-C09', 1800,  600, 1, 'INTERNAL', FALSE, 1.0),

-- Row 4: Shelf row with charging station
('RP-R04-C00',    0,  800, 1, 'CHARGE',   FALSE, 0.5),
('RP-R04-C01',  200,  800, 1, 'INTERNAL', FALSE, 1.0),
('RP-R04-C02',  400,  800, 1, 'INTERNAL', FALSE, 0.8),
('RP-R04-C03',  600,  800, 1, 'INTERNAL', FALSE, 1.0),
('RP-R04-C04',  800,  800, 1, 'INTERNAL', FALSE, 0.8),
('RP-R04-C05', 1000,  800, 1, 'INTERNAL', FALSE, 1.0),
('RP-R04-C06', 1200,  800, 1, 'INTERNAL', FALSE, 0.8),
('RP-R04-C07', 1400,  800, 1, 'INTERNAL', FALSE, 1.0),
('RP-R04-C08', 1600,  800, 1, 'INTERNAL', FALSE, 0.8),
('RP-R04-C09', 1800,  800, 1, 'INTERNAL', FALSE, 1.0),

-- Row 5: Bottom corridor with charging stations
('RP-R05-C00',    0, 1000, 1, 'CHARGE',   FALSE, 0.5),
('RP-R05-C01',  200, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C02',  400, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C03',  600, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C04',  800, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C05', 1000, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C06', 1200, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C07', 1400, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C08', 1600, 1000, 1, 'INTERNAL', FALSE, 1.0),
('RP-R05-C09', 1800, 1000, 1, 'CHARGE',   FALSE, 0.5)
ON CONFLICT (code) DO UPDATE SET
    x = EXCLUDED.x, y = EXCLUDED.y, z_level = EXCLUDED.z_level,
    type = EXCLUDED.type, blocked = EXCLUDED.blocked, max_speed = EXCLUDED.max_speed;

-- ============================================================
-- Route Edges: connect all adjacent non-blocked cells (4 directions)
-- Distance = cellSize (200) for cardinal directions
-- All bidirectional
-- ============================================================
INSERT INTO route_edge (source_id, target_id, distance, bidirectional, weight)
SELECT s.id, t.id, 200.0, TRUE, 200.0
FROM (VALUES
    -- Horizontal edges: Row 0 (C0-C1, C1-C2, ..., C8-C9)
    ('RP-R00-C00','RP-R00-C01'), ('RP-R00-C01','RP-R00-C02'),
    ('RP-R00-C02','RP-R00-C03'), ('RP-R00-C03','RP-R00-C04'),
    ('RP-R00-C04','RP-R00-C05'), ('RP-R00-C05','RP-R00-C06'),
    ('RP-R00-C06','RP-R00-C07'), ('RP-R00-C07','RP-R00-C08'),
    ('RP-R00-C08','RP-R00-C09'),
    -- Horizontal edges: Row 1
    ('RP-R01-C00','RP-R01-C01'), ('RP-R01-C01','RP-R01-C02'),
    ('RP-R01-C02','RP-R01-C03'), ('RP-R01-C03','RP-R01-C04'),
    ('RP-R01-C04','RP-R01-C05'), ('RP-R01-C05','RP-R01-C06'),
    ('RP-R01-C06','RP-R01-C07'), ('RP-R01-C07','RP-R01-C08'),
    ('RP-R01-C08','RP-R01-C09'),
    -- Horizontal edges: Row 2
    ('RP-R02-C00','RP-R02-C01'), ('RP-R02-C01','RP-R02-C02'),
    ('RP-R02-C02','RP-R02-C03'), ('RP-R02-C03','RP-R02-C04'),
    ('RP-R02-C04','RP-R02-C05'), ('RP-R02-C05','RP-R02-C06'),
    ('RP-R02-C06','RP-R02-C07'), ('RP-R02-C07','RP-R02-C08'),
    ('RP-R02-C08','RP-R02-C09'),
    -- Horizontal edges: Row 3
    ('RP-R03-C00','RP-R03-C01'), ('RP-R03-C01','RP-R03-C02'),
    ('RP-R03-C02','RP-R03-C03'), ('RP-R03-C03','RP-R03-C04'),
    ('RP-R03-C04','RP-R03-C05'), ('RP-R03-C05','RP-R03-C06'),
    ('RP-R03-C06','RP-R03-C07'), ('RP-R03-C07','RP-R03-C08'),
    ('RP-R03-C08','RP-R03-C09'),
    -- Horizontal edges: Row 4
    ('RP-R04-C00','RP-R04-C01'), ('RP-R04-C01','RP-R04-C02'),
    ('RP-R04-C02','RP-R04-C03'), ('RP-R04-C03','RP-R04-C04'),
    ('RP-R04-C04','RP-R04-C05'), ('RP-R04-C05','RP-R04-C06'),
    ('RP-R04-C06','RP-R04-C07'), ('RP-R04-C07','RP-R04-C08'),
    ('RP-R04-C08','RP-R04-C09'),
    -- Horizontal edges: Row 5
    ('RP-R05-C00','RP-R05-C01'), ('RP-R05-C01','RP-R05-C02'),
    ('RP-R05-C02','RP-R05-C03'), ('RP-R05-C03','RP-R05-C04'),
    ('RP-R05-C04','RP-R05-C05'), ('RP-R05-C05','RP-R05-C06'),
    ('RP-R05-C06','RP-R05-C07'), ('RP-R05-C07','RP-R05-C08'),
    ('RP-R05-C08','RP-R05-C09'),
    -- Vertical edges: Col 0 (R0-R1, R1-R2, ..., R4-R5)
    ('RP-R00-C00','RP-R01-C00'), ('RP-R01-C00','RP-R02-C00'),
    ('RP-R02-C00','RP-R03-C00'), ('RP-R03-C00','RP-R04-C00'),
    ('RP-R04-C00','RP-R05-C00'),
    -- Vertical edges: Col 1
    ('RP-R00-C01','RP-R01-C01'), ('RP-R01-C01','RP-R02-C01'),
    ('RP-R02-C01','RP-R03-C01'), ('RP-R03-C01','RP-R04-C01'),
    ('RP-R04-C01','RP-R05-C01'),
    -- Vertical edges: Col 2
    ('RP-R00-C02','RP-R01-C02'), ('RP-R01-C02','RP-R02-C02'),
    ('RP-R02-C02','RP-R03-C02'), ('RP-R03-C02','RP-R04-C02'),
    ('RP-R04-C02','RP-R05-C02'),
    -- Vertical edges: Col 3
    ('RP-R00-C03','RP-R01-C03'), ('RP-R01-C03','RP-R02-C03'),
    ('RP-R02-C03','RP-R03-C03'), ('RP-R03-C03','RP-R04-C03'),
    ('RP-R04-C03','RP-R05-C03'),
    -- Vertical edges: Col 4
    ('RP-R00-C04','RP-R01-C04'), ('RP-R01-C04','RP-R02-C04'),
    ('RP-R02-C04','RP-R03-C04'), ('RP-R03-C04','RP-R04-C04'),
    ('RP-R04-C04','RP-R05-C04'),
    -- Vertical edges: Col 5
    ('RP-R00-C05','RP-R01-C05'), ('RP-R01-C05','RP-R02-C05'),
    ('RP-R02-C05','RP-R03-C05'), ('RP-R03-C05','RP-R04-C05'),
    ('RP-R04-C05','RP-R05-C05'),
    -- Vertical edges: Col 6
    ('RP-R00-C06','RP-R01-C06'), ('RP-R01-C06','RP-R02-C06'),
    ('RP-R02-C06','RP-R03-C06'), ('RP-R03-C06','RP-R04-C06'),
    ('RP-R04-C06','RP-R05-C06'),
    -- Vertical edges: Col 7
    ('RP-R00-C07','RP-R01-C07'), ('RP-R01-C07','RP-R02-C07'),
    ('RP-R02-C07','RP-R03-C07'), ('RP-R03-C07','RP-R04-C07'),
    ('RP-R04-C07','RP-R05-C07'),
    -- Vertical edges: Col 8
    ('RP-R00-C08','RP-R01-C08'), ('RP-R01-C08','RP-R02-C08'),
    ('RP-R02-C08','RP-R03-C08'), ('RP-R03-C08','RP-R04-C08'),
    ('RP-R04-C08','RP-R05-C08'),
    -- Vertical edges: Col 9
    ('RP-R00-C09','RP-R01-C09'), ('RP-R01-C09','RP-R02-C09'),
    ('RP-R02-C09','RP-R03-C09'), ('RP-R03-C09','RP-R04-C09'),
    ('RP-R04-C09','RP-R05-C09')
) AS data(source_code, target_code)
JOIN root_point s ON s.code = data.source_code
JOIN root_point t ON t.code = data.target_code
ON CONFLICT (source_id, target_id) DO NOTHING;

-- ============================================================
-- Spots (linked to root_points, representing storage locations)
-- Spot position is offset from root_point (on the shelf side)
-- ============================================================
INSERT INTO spot (code, aisle, section, level, root_point_id, x, y, z) VALUES
-- Aisle A (Row 2): shelves facing the corridor
('SP-A1-01', 'A', '1', 1, (SELECT id FROM root_point WHERE code='RP-R02-C01'), 200, 380, 1),
('SP-A1-02', 'A', '1', 2, (SELECT id FROM root_point WHERE code='RP-R02-C03'), 600, 380, 2),
('SP-A1-03', 'A', '1', 3, (SELECT id FROM root_point WHERE code='RP-R02-C05'), 1000, 380, 3),
('SP-A1-04', 'A', '2', 1, (SELECT id FROM root_point WHERE code='RP-R02-C07'), 1400, 380, 1),
-- Aisle B (Row 4): shelves facing the corridor
('SP-B1-01', 'B', '1', 1, (SELECT id FROM root_point WHERE code='RP-R04-C02'), 400, 780, 1),
('SP-B1-02', 'B', '1', 2, (SELECT id FROM root_point WHERE code='RP-R04-C04'), 800, 780, 2),
('SP-B1-03', 'B', '1', 3, (SELECT id FROM root_point WHERE code='RP-R04-C06'), 1200, 780, 3),
('SP-B1-04', 'B', '2', 1, (SELECT id FROM root_point WHERE code='RP-R04-C08'), 1600, 780, 1)
ON CONFLICT (code) DO UPDATE SET
    aisle = EXCLUDED.aisle, section = EXCLUDED.section,
    level = EXCLUDED.level, root_point_id = EXCLUDED.root_point_id,
    x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z;

-- Re-seed inventory items into new spots
INSERT INTO spot_item (spot_id, item_id, quantity_available, quantity_reserved)
SELECT s.id, i.id, qty, 0
FROM (VALUES
    ('SP-A1-01', 'SKU-ELEC-001', 15),
    ('SP-A1-02', 'SKU-ELEC-002', 8),
    ('SP-A1-03', 'SKU-HOGAR-001', 20),
    ('SP-A1-04', 'SKU-CRISTAL-001', 12),
    ('SP-B1-01', 'SKU-ROPA-001', 30),
    ('SP-B1-02', 'SKU-ELEC-001', 5),
    ('SP-B1-03', 'SKU-ELEC-002', 3),
    ('SP-B1-04', 'SKU-HOGAR-001', 8),
    ('SP-A1-01', 'SKU-ROPA-001', 10),
    ('SP-A1-03', 'SKU-CRISTAL-001', 6)
) AS data(spot_code, sku_val, qty)
JOIN spot s ON s.code = data.spot_code
JOIN inventory_item i ON i.sku = data.sku_val
ON CONFLICT (spot_id, item_id) DO NOTHING;