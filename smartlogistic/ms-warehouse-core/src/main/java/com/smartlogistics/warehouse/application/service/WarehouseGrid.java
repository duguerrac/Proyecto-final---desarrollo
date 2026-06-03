package com.smartlogistics.warehouse.application.service;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory grid representing the warehouse floor plan.
 * Each cell is identified by (row, col) and has an availability flag:
 *   available = true  → robot can move through this cell
 *   available = false → cell is blocked (shelf, obstacle, or occupied by a robot)
 *
 * This is the single source of truth for pathfinding.
 * The grid is built from the DB root_points on load/reload and
 * updated dynamically when robots move.
 *
 * Grid layout (6 rows × 10 cols, from V9):
 *   Row 0: START   EMPTY   SHELF   EMPTY   SHELF   EMPTY   SHELF   EMPTY   SHELF   EXIT
 *   Row 1: EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY  ← corridor
 *   Row 2: EMPTY   SHELF   EMPTY   SHELF   EMPTY   SHELF   EMPTY   SHELF   EMPTY   EMPTY
 *   Row 3: EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY  ← corridor
 *   Row 4: CHARGE  EMPTY   SHELF   EMPTY   SHELF   EMPTY   SHELF   EMPTY   SHELF   EMPTY
 *   Row 5: CHARGE  EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   EMPTY   CHARGE ← corridor
 */
public class WarehouseGrid {

    private static final Logger log = LoggerFactory.getLogger(WarehouseGrid.class);

    /** Cell states */
    public enum CellState {
        /** Robot can walk through this cell */
        AVAILABLE(true),
        /** Static obstacle: shelf or obstacle — permanently blocked */
        SHELF(false),
        /** Dynamic obstacle: occupied by a robot — changes as robots move */
        ROBOT_OCCUPIED(false);

        public final boolean walkable;
        CellState(boolean walkable) { this.walkable = walkable; }
    }

    private final int rows;
    private final int cols;
    private final CellState[][] grid;
    private final double cellSize;

    public WarehouseGrid(int rows, int cols, double cellSize) {
        this.rows = rows;
        this.cols = cols;
        this.cellSize = cellSize;
        this.grid = new CellState[rows][cols];
        // Initialize all cells as AVAILABLE
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = CellState.AVAILABLE;
            }
        }
    }

    /** Mark a cell as a shelf (static obstacle) */
    public void setShelf(int row, int col) {
        if (inBounds(row, col)) {
            grid[row][col] = CellState.SHELF;
        }
    }

    /** Mark a cell as occupied by a robot (dynamic obstacle) */
    public void setRobotOccupied(int row, int col) {
        if (inBounds(row, col) && grid[row][col] != CellState.SHELF) {
            grid[row][col] = CellState.ROBOT_OCCUPIED;
        }
    }

    /** Clear a robot occupation (robot left the cell) */
    public void clearRobotOccupied(int row, int col) {
        if (inBounds(row, col) && grid[row][col] == CellState.ROBOT_OCCUPIED) {
            grid[row][col] = CellState.AVAILABLE;
        }
    }

    /** Check if a cell is walkable (available for robot movement) */
    public boolean isAvailable(int row, int col) {
        if (!inBounds(row, col)) return false;
        return grid[row][col].walkable;
    }

    /** Get the state of a cell */
    public CellState getCellState(int row, int col) {
        if (!inBounds(row, col)) return CellState.SHELF; // out of bounds = blocked
        return grid[row][col];
    }

    /** Check if a cell is a shelf */
    public boolean isShelf(int row, int col) {
        if (!inBounds(row, col)) return false;
        return grid[row][col] == CellState.SHELF;
    }

    /** Check if a cell is occupied by a robot */
    public boolean isRobotOccupied(int row, int col) {
        if (!inBounds(row, col)) return false;
        return grid[row][col] == CellState.ROBOT_OCCUPIED;
    }

    private boolean inBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public double getCellSize() { return cellSize; }

    /**
     * Get the 4-directional neighbors of a cell that are walkable.
     * Used by pathfinding to find available adjacent cells.
     *
     * @param row current row
     * @param col current col
     * @param allowDestination if true, the destination cell is considered walkable even if robot-occupied
     * @param destRow destination row (only used if allowDestination is true)
     * @param destCol destination col (only used if allowDestination is true)
     * @return list of (row, col) pairs for walkable neighbors
     */
    public List<int[]> getWalkableNeighbors(int row, int col, boolean allowDestination, int destRow, int destCol) {
        List<int[]> neighbors = new ArrayList<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : dirs) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (!inBounds(nr, nc)) continue;

            CellState state = grid[nr][nc];
            if (state == CellState.AVAILABLE) {
                neighbors.add(new int[]{nr, nc});
            } else if (allowDestination && nr == destRow && nc == destCol) {
                // Allow the destination even if robot-occupied
                neighbors.add(new int[]{nr, nc});
            }
        }
        return neighbors;
    }

    /**
     * Dump the grid state as a visual string for debugging.
     */
    public String toVisualString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Warehouse Grid (").append(rows).append("x").append(cols).append("):\n");
        sb.append("  Legend: . = available, S = shelf, R = robot, # = out-of-bounds\n");
        for (int r = 0; r < rows; r++) {
            sb.append(String.format("  R%02d: ", r));
            for (int c = 0; c < cols; c++) {
                switch (grid[r][c]) {
                    case AVAILABLE -> sb.append(". ");
                    case SHELF -> sb.append("S ");
                    case ROBOT_OCCUPIED -> sb.append("R ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
