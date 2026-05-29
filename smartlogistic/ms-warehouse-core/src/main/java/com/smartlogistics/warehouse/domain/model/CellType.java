package com.smartlogistics.warehouse.domain.model;

/**
 * Types of cells in a warehouse layout grid.
 * Each type maps to specific behavior when the layout is activated.
 */
public enum CellType {
    /** Walkable corridor - generates INTERNAL root_point */
    EMPTY,
    /** Storage shelf - generates SHELF root_point + spot */
    SHELF,
    /** Robot charging station - generates CHARGING root_point */
    CHARGING,
    /** Order exit point - generates EXIT root_point */
    DELIVERY_DOCK,
    /** Order entry point - generates START root_point */
    RECEIVING_DOCK,
    /** Robot spawn/starting point - generates SPAWN root_point */
    ROBOT_SPAWN,
    /** Blocked cell - no root_point, no edges generated */
    OBSTACLE;

    /**
     * Maps a CellType to the corresponding root_point type string.
     * OBSTACLE returns null (no root_point generated).
     */
    public String toRootPointType() {
        return switch (this) {
            case EMPTY -> "INTERNAL";
            case SHELF -> "SHELF";
            case CHARGING -> "CHARGING";
            case DELIVERY_DOCK -> "EXIT";
            case RECEIVING_DOCK -> "START";
            case ROBOT_SPAWN -> "SPAWN";
            case OBSTACLE -> null;
        };
    }

    /**
     * Whether this cell type generates a spot (storage location).
     */
    public boolean generatesSpot() {
        return this == SHELF;
    }

    /**
     * Whether this cell type generates a root_point (navigable node).
     */
    public boolean generatesRootPoint() {
        return this != OBSTACLE;
    }
}