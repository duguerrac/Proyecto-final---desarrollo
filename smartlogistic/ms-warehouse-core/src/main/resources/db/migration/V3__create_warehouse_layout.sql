-- ============================================================
-- V3: Warehouse Layout - Grid-based layout definition
-- Allows defining warehouse floor plans as a grid of cells
-- which are then converted to root_points, route_edges, spots
-- when the layout is activated.
-- ============================================================

CREATE TABLE IF NOT EXISTS warehouse_layout (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    rows_count INT NOT NULL,
    cols_count INT NOT NULL,
    cell_size DECIMAL(8,2) NOT NULL DEFAULT 1.0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layout_cell (
    id BIGSERIAL PRIMARY KEY,
    layout_id BIGINT NOT NULL REFERENCES warehouse_layout(id) ON DELETE CASCADE,
    row_index INT NOT NULL,
    col_index INT NOT NULL,
    cell_type VARCHAR(30) NOT NULL DEFAULT 'EMPTY',
    UNIQUE(layout_id, row_index, col_index)
);

CREATE INDEX IF NOT EXISTS idx_layout_cell_layout_id ON layout_cell(layout_id);

-- Cell types:
-- EMPTY        -> walkable corridor (generates INTERNAL root_point)
-- SHELF        -> storage shelf (generates SHELF root_point + spot)
-- CHARGING     -> robot charging station (generates CHARGING root_point)
-- DELIVERY_DOCK -> order exit point (generates EXIT root_point)
-- RECEIVING_DOCK -> order entry point (generates START root_point)
-- ROBOT_SPAWN  -> robot spawn point (generates SPAWN root_point)
-- OBSTACLE     -> blocked cell (no root_point, no edges)