-- ============================================================
-- V12: Fix shelf root_points and route_edges
--
-- Problem: V8 created root_points for SHELF cells with
--   type='INTERNAL' and blocked=FALSE, and created route_edges
--   through them. This caused pathfinding to route robots
--   through shelves.
--
-- Fix:
--   1. Set type='SHELF' and blocked=TRUE for all shelf root_points
--   2. Delete route_edges that connect through shelf cells
--      (source or target is a shelf root_point)
--
-- NOTE: After this migration, call POST /api/routes/graph/reload
--   to reload the in-memory route graph, or restart the service.
-- ============================================================

-- 1. Update shelf root_points: set type='SHELF' and blocked=TRUE
--    Shelf positions match V9 layout:
--      Row 0: cols 2, 4, 6, 8
--      Row 2: cols 1, 3, 5, 7
--      Row 4: cols 2, 4, 6, 8
UPDATE root_point
SET type = 'SHELF', blocked = TRUE
WHERE code IN (
    -- Row 0: SHELF at cols 2, 4, 6, 8
    'RP-R00-C02', 'RP-R00-C04', 'RP-R00-C06', 'RP-R00-C08',
    -- Row 2: SHELF at cols 1, 3, 5, 7
    'RP-R02-C01', 'RP-R02-C03', 'RP-R02-C05', 'RP-R02-C07',
    -- Row 4: SHELF at cols 2, 4, 6, 8
    'RP-R04-C02', 'RP-R04-C04', 'RP-R04-C06', 'RP-R04-C08'
);

-- 2. Delete route_edges where source or target is a shelf root_point
DELETE FROM route_edge
WHERE source_id IN (
    SELECT id FROM root_point WHERE type = 'SHELF'
) OR target_id IN (
    SELECT id FROM root_point WHERE type = 'SHELF'
);
