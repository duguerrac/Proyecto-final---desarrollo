-- ============================================================
-- V11: Seed spot_item rows so orders can resolve SKU → spot
-- Uses existing inventory_item SKUs from V4 and spots from V8/V9
-- ============================================================

-- Add the SKUs used by the order flow
INSERT INTO inventory_item (sku, name) VALUES
    ('SKU-ROPA-001', 'Camiseta Basica'),
    ('SKU-ROPA-002', 'Pantalon Jean'),
    ('SKU-CALZ-001', 'Zapato Deportivo'),
    ('SKU-ELEC-001', 'Electronics Component A'),
    ('SKU-MECH-002', 'Mechanical Part B'),
    ('SKU-SENS-003', 'Sensor Module C'),
    ('SKU-CABL-004', 'Cable Harness D'),
    ('SKU-MOTO-005', 'Motor Unit E')
ON CONFLICT (sku) DO NOTHING;

-- Link SKUs to spots via spot_item
INSERT INTO spot_item (spot_id, item_id, quantity_available, quantity_reserved)
SELECT s.id, i.id, 50, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-ROPA-001' WHERE s.code = 'SP-A1-01'
UNION ALL
SELECT s.id, i.id, 30, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-ROPA-002' WHERE s.code = 'SP-A1-02'
UNION ALL
SELECT s.id, i.id, 25, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-CALZ-001' WHERE s.code = 'SP-A1-03'
UNION ALL
SELECT s.id, i.id, 40, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-ELEC-001' WHERE s.code = 'SP-A1-04'
UNION ALL
SELECT s.id, i.id, 15, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-MECH-002' WHERE s.code = 'SP-B1-01'
UNION ALL
SELECT s.id, i.id, 20, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-SENS-003' WHERE s.code = 'SP-B1-02'
UNION ALL
SELECT s.id, i.id, 100, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-CABL-004' WHERE s.code = 'SP-B1-03'
UNION ALL
SELECT s.id, i.id, 10, 0
FROM spot s JOIN inventory_item i ON i.sku = 'SKU-MOTO-005' WHERE s.code = 'SP-B1-04'
ON CONFLICT DO NOTHING;
