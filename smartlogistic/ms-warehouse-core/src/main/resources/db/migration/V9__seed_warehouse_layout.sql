-- ============================================================
-- V9: Seed warehouse_layout + layout_cell for UE5 dynamic building
-- Grid: 6 rows × 10 cols, cellSize = 200 (matches V8 root_points)
-- UE5 calls GET /api/layouts/active to fetch this layout
-- ============================================================

-- Insert active layout
INSERT INTO warehouse_layout (name, rows_count, cols_count, cell_size, status, created_at, activated_at)
VALUES ('SmartLogistics Main Warehouse', 6, 10, 200.0, 'ACTIVE', NOW(), NOW());

-- Insert layout cells matching V8 grid:
--   Row 0: RECEIVING  EMPTY    SHELF    EMPTY    SHELF    EMPTY    SHELF    EMPTY    SHELF    DELIVERY
--   Row 1: EMPTY      EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY
--   Row 2: EMPTY      SHELF    EMPTY    SHELF    EMPTY    SHELF    EMPTY    SHELF    EMPTY    EMPTY
--   Row 3: EMPTY      EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY
--   Row 4: CHARGING   EMPTY    SHELF    EMPTY    SHELF    EMPTY    SHELF    EMPTY    SHELF    EMPTY
--   Row 5: CHARGING   EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    EMPTY    CHARGING

INSERT INTO layout_cell (layout_id, row_index, col_index, cell_type) VALUES
-- Row 0: Top corridor (receiving dock → shelves → delivery dock)
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 0, 'RECEIVING_DOCK'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 1, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 2, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 3, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 4, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 5, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 6, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 7, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 8, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 0, 9, 'DELIVERY_DOCK'),

-- Row 1: Main corridor (full walkable)
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 0, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 1, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 2, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 3, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 4, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 5, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 6, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 7, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 8, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 1, 9, 'EMPTY'),

-- Row 2: Shelf row (shelves with corridor gaps)
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 0, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 1, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 2, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 3, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 4, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 5, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 6, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 7, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 8, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 2, 9, 'EMPTY'),

-- Row 3: Middle corridor (full walkable)
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 0, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 1, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 2, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 3, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 4, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 5, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 6, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 7, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 8, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 3, 9, 'EMPTY'),

-- Row 4: Shelf row with charging station at start
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 0, 'CHARGING'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 1, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 2, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 3, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 4, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 5, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 6, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 7, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 8, 'SHELF'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 4, 9, 'EMPTY'),

-- Row 5: Bottom corridor with charging stations
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 0, 'CHARGING'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 1, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 2, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 3, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 4, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 5, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 6, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 7, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 8, 'EMPTY'),
((SELECT id FROM warehouse_layout WHERE status = 'ACTIVE'), 5, 9, 'CHARGING');