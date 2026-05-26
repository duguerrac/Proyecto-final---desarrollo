-- =============================================================
-- SmartLogistic — Seed Data para MS-WarehouseCore
-- =============================================================
-- Este script se ejecuta automáticamente al iniciar PostgreSQL
-- Montado en: /docker-entrypoint-initdb.d/seed-warehouse.sql
-- =============================================================

-- Tablas del dominio
-- (Las tablas serán creadas por JPA/Hibernate en Fase C,
--  este seed se ejecuta con CREATE IF NOT EXISTS para ser idempotente)

CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    fragile BOOLEAN NOT NULL DEFAULT FALSE,
    default_speed_limit DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    weight_kg DECIMAL(8,2) NOT NULL DEFAULT 0,
    dimensions VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS spot (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    aisle VARCHAR(20) NOT NULL,
    section VARCHAR(20) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    root_point_id BIGINT,
    x DECIMAL(8,2),
    y DECIMAL(8,2),
    z DECIMAL(8,2)
);

CREATE TABLE IF NOT EXISTS spot_item (
    id BIGSERIAL PRIMARY KEY,
    spot_id BIGINT NOT NULL REFERENCES spot(id),
    item_id BIGINT NOT NULL REFERENCES inventory_item(id),
    quantity_available INT NOT NULL DEFAULT 0,
    UNIQUE(spot_id, item_id)
);

CREATE TABLE IF NOT EXISTS dispatch_order (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    assigned_robot_id VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES dispatch_order(id),
    item_id BIGINT NOT NULL REFERENCES inventory_item(id),
    requested_quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS root_point (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    x DECIMAL(8,2) NOT NULL,
    y DECIMAL(8,2) NOT NULL,
    z_level INT NOT NULL DEFAULT 1,
    type VARCHAR(30) NOT NULL DEFAULT 'INTERNAL',
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    max_speed DECIMAL(5,2) NOT NULL DEFAULT 1.0
);

CREATE TABLE IF NOT EXISTS route_edge (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES root_point(id),
    target_id BIGINT NOT NULL REFERENCES root_point(id),
    distance DECIMAL(8,2) NOT NULL,
    bidirectional BOOLEAN NOT NULL DEFAULT TRUE,
    weight DECIMAL(8,2) NOT NULL DEFAULT 1.0
);

CREATE TABLE IF NOT EXISTS route_plan (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES dispatch_order(id),
    robot_id VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_distance DECIMAL(10,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS route_step (
    id BIGSERIAL PRIMARY KEY,
    route_plan_id BIGINT NOT NULL REFERENCES route_plan(id),
    sequence INT NOT NULL,
    root_point_id BIGINT NOT NULL REFERENCES root_point(id),
    action VARCHAR(50) NOT NULL DEFAULT 'NAVIGATE',
    UNIQUE(route_plan_id, sequence)
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP
);

-- =============================================================
-- Datos semilla: Inventory Items (5 productos)
-- =============================================================
INSERT INTO inventory_item (sku, name, fragile, default_speed_limit, weight_kg, dimensions) VALUES
    ('SKU-ELEC-001', 'Laptop ProBook 450', FALSE, 1.0, 2.5, '35x25x3 cm'),
    ('SKU-ELEC-002', 'Monitor 27 pulgadas', TRUE, 0.4, 5.0, '70x50x20 cm'),
    ('SKU-HOGAR-001', 'Set de Sartenes Antiadherentes', FALSE, 1.0, 3.2, '40x30x15 cm'),
    ('SKU-CRISTAL-001', 'Juego de Copas de Cristal', TRUE, 0.3, 1.8, '30x25x25 cm'),
    ('SKU-ROPA-001', 'Caja de Camisetas x50', FALSE, 1.0, 8.0, '60x40x30 cm')
ON CONFLICT (sku) DO NOTHING;

-- =============================================================
-- Datos semilla: Root Points (10 puntos navegables)
-- =============================================================
INSERT INTO root_point (code, x, y, z_level, type, blocked, max_speed) VALUES
    ('RP-START',   0.0,   0.0,  1, 'START',    FALSE, 1.0),
    ('RP-A1-01',  10.0,  0.0,  1, 'INTERNAL', FALSE, 1.0),
    ('RP-A1-02',  20.0,  0.0,  1, 'INTERNAL', FALSE, 1.0),
    ('RP-A1-03',  30.0,  0.0,  1, 'INTERNAL', FALSE, 1.0),
    ('RP-B1-01',  10.0, 10.0,  1, 'INTERNAL', FALSE, 1.0),
    ('RP-B1-02',  20.0, 10.0,  1, 'INTERNAL', FALSE, 1.0),
    ('RP-B1-03',  30.0, 10.0,  1, 'INTERNAL', FALSE, 1.0),
    ('RP-CROSS',  15.0,  5.0,  1, 'CROSS',    FALSE, 0.6),
    ('RP-CHARGE',  5.0, 15.0,  1, 'CHARGE',   FALSE, 0.5),
    ('RP-EXIT',   35.0, 10.0,  1, 'EXIT',     FALSE, 1.0)
ON CONFLICT (code) DO NOTHING;

-- =============================================================
-- Datos semilla: Spots (5 ubicaciones físicas)
-- =============================================================
INSERT INTO spot (code, aisle, section, level, root_point_id, x, y, z) VALUES
    ('SP-A1-01', 'A', '1', 1, (SELECT id FROM root_point WHERE code = 'RP-A1-01'), 10.0, 2.0, 1.0),
    ('SP-A1-02', 'A', '1', 2, (SELECT id FROM root_point WHERE code = 'RP-A1-02'), 20.0, 2.0, 2.0),
    ('SP-A1-03', 'A', '1', 3, (SELECT id FROM root_point WHERE code = 'RP-A1-03'), 30.0, 2.0, 3.0),
    ('SP-B1-01', 'B', '1', 1, (SELECT id FROM root_point WHERE code = 'RP-B1-01'), 10.0, 12.0, 1.0),
    ('SP-B1-02', 'B', '1', 2, (SELECT id FROM root_point WHERE code = 'RP-B1-02'), 20.0, 12.0, 2.0)
ON CONFLICT (code) DO NOTHING;

-- =============================================================
-- Datos semilla: Spot-Item (10 relaciones)
-- =============================================================
INSERT INTO spot_item (spot_id, item_id, quantity_available)
SELECT s.id, i.id, qty
FROM (VALUES
    ('SP-A1-01', 'SKU-ELEC-001', 15),
    ('SP-A1-02', 'SKU-ELEC-002', 8),
    ('SP-A1-03', 'SKU-HOGAR-001', 20),
    ('SP-B1-01', 'SKU-CRISTAL-001', 12),
    ('SP-B1-02', 'SKU-ROPA-001', 30),
    ('SP-A1-01', 'SKU-ROPA-001', 10),
    ('SP-A1-02', 'SKU-ELEC-001', 5),
    ('SP-B1-01', 'SKU-HOGAR-001', 8),
    ('SP-B1-02', 'SKU-ELEC-002', 3),
    ('SP-A1-03', 'SKU-CRISTAL-001', 6)
) AS data(spot_code, sku_val, qty)
JOIN spot s ON s.code = data.spot_code
JOIN inventory_item i ON i.sku = data.sku_val;

-- =============================================================
-- Datos semilla: Route Edges (15 conexiones)
-- =============================================================
INSERT INTO route_edge (source_id, target_id, distance, bidirectional, weight)
SELECT s.id, t.id, dist, TRUE, dist
FROM (VALUES
    ('RP-START',  'RP-A1-01', 10.0),
    ('RP-START',  'RP-B1-01', 14.1),
    ('RP-START',  'RP-CHARGE',  7.1),
    ('RP-A1-01',  'RP-A1-02', 10.0),
    ('RP-A1-01',  'RP-CROSS',  7.1),
    ('RP-A1-02',  'RP-A1-03', 10.0),
    ('RP-A1-02',  'RP-B1-02', 10.0),
    ('RP-A1-03',  'RP-EXIT',   7.1),
    ('RP-B1-01',  'RP-B1-02', 10.0),
    ('RP-B1-01',  'RP-CROSS',  7.1),
    ('RP-B1-02',  'RP-B1-03', 10.0),
    ('RP-B1-02',  'RP-A1-02', 10.0),
    ('RP-B1-03',  'RP-EXIT',   7.1),
    ('RP-CROSS',  'RP-A1-02',  7.1),
    ('RP-CHARGE', 'RP-B1-01',  7.1)
) AS data(source_code, target_code, dist)
JOIN root_point s ON s.code = data.source_code
JOIN root_point t ON t.code = data.target_code;
