package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;

/**
 * Represents a navigable point in the warehouse floor plan.
 * Used by the robot pathfinding graph.
 *
 * @param id      unique identifier (assigned as row * cols + col + 1)
 * @param code    human-readable code (e.g. "RP-0-3")
 * @param x       world X coordinate
 * @param y       world Y coordinate
 * @param zLevel  floor level (always 1 for single-floor warehouses)
 * @param type    point type: INTERNAL, SHELF, CHARGING, EXIT, START, SPAWN
 * @param blocked whether the point is currently blocked
 */
public record RootPoint(
        Long id,
        String code,
        BigDecimal x,
        BigDecimal y,
        int zLevel,
        String type,
        boolean blocked
) {}