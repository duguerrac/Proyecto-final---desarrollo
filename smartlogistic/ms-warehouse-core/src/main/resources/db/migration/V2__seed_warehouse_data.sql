INSERT INTO inventory_item (sku, name, fragile, default_speed_limit, weight_kg, dimensions) VALUES
    ('SKU-ELEC-001', 'Laptop ProBook 450', FALSE, 1.0, 2.5, '35x25x3 cm'),
    ('SKU-ELEC-002', 'Monitor 27 pulgadas', TRUE, 0.4, 5.0, '70x50x20 cm'),
    ('SKU-HOGAR-001', 'Set de Sartenes Antiadherentes', FALSE, 1.0, 3.2, '40x30x15 cm'),
    ('SKU-CRISTAL-001', 'Juego de Copas de Cristal', TRUE, 0.3, 1.8, '30x25x25 cm'),
    ('SKU-ROPA-001', 'Caja de Camisetas x50', FALSE, 1.0, 8.0, '60x40x30 cm')
ON CONFLICT (sku) DO NOTHING;

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

INSERT INTO spot (code, aisle, section, level, root_point_id, x, y, z) VALUES
    ('SP-A1-01', 'A', '1', 1, (SELECT id FROM root_point WHERE code = 'RP-A1-01'), 10.0, 2.0, 1.0),
    ('SP-A1-02', 'A', '1', 2, (SELECT id FROM root_point WHERE code = 'RP-A1-02'), 20.0, 2.0, 2.0),
    ('SP-A1-03', 'A', '1', 3, (SELECT id FROM root_point WHERE code = 'RP-A1-03'), 30.0, 2.0, 3.0),
    ('SP-B1-01', 'B', '1', 1, (SELECT id FROM root_point WHERE code = 'RP-B1-01'), 10.0, 12.0, 1.0),
    ('SP-B1-02', 'B', '1', 2, (SELECT id FROM root_point WHERE code = 'RP-B1-02'), 20.0, 12.0, 2.0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO spot_item (spot_id, item_id, quantity_available, quantity_reserved)
SELECT s.id, i.id, qty, 0
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
JOIN inventory_item i ON i.sku = data.sku_val
ON CONFLICT (spot_id, item_id) DO NOTHING;

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
JOIN root_point t ON t.code = data.target_code
ON CONFLICT (source_id, target_id) DO NOTHING;
