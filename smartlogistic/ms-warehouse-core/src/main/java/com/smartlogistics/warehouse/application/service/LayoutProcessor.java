package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.application.port.out.LayoutRepositoryPort;
import com.smartlogistics.warehouse.domain.model.*;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes a warehouse layout grid and converts it into:
 * - root_points (navigable nodes for the robot pathfinding graph)
 * - route_edges (connections between adjacent navigable nodes)
 * - spots (storage locations linked to shelf root_points)
 *
 * Grid coordinate system (aligned with Unreal Engine CellToWorldPosition):
 * - Each cell's world position: x = (row + 0.5) * cellSize, y = (col + 0.5) * cellSize
 * - row 0, col 0 is at the origin corner; cells extend in +X and +Y
 * - root_point IDs are assigned as: (long) row * cols + col + 1
 * - Spot codes: S-{row_letter}{col+1} e.g. S-A01 (matches UE convention)
 */
@Service
public class LayoutProcessor {

    private static final Logger log = LoggerFactory.getLogger(LayoutProcessor.class);
    private final LayoutRepositoryPort repository;

    public LayoutProcessor(LayoutRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional
    public void activateLayout(WarehouseLayout layout) {
        log.info("Activating layout '{}' ({}x{}, cellSize={})",
                layout.name(), layout.rowsCount(), layout.colsCount(), layout.cellSize());

        // 1. Archive any currently active layout
        repository.archiveActive();

        // 2. Clear existing root_points, edges, spots
        repository.deleteAllRootPoints();

        int rows = layout.rowsCount();
        int cols = layout.colsCount();
        BigDecimal cellSize = layout.cellSize();

        // 3. Build a 2D grid of cells for quick lookup
        CellType[][] grid = new CellType[rows][cols];
        for (LayoutCell cell : layout.cells()) {
            grid[cell.rowIndex()][cell.colIndex()] = cell.cellType();
        }
        // Fill missing cells as EMPTY
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == null) {
                    grid[r][c] = CellType.EMPTY;
                }
            }
        }

        // 4. Generate root_points for non-OBSTACLE cells
        // ID scheme: row * cols + col + 1
        long[][] pointIds = new long[rows][cols];
        int shelfCount = 0;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                CellType type = grid[r][c];
                if (!type.generatesRootPoint()) {
                    pointIds[r][c] = 0;
                    continue;
                }

                long id = (long) r * cols + c + 1;
                pointIds[r][c] = id;

                // World position aligned with UE: CellToWorldPosition(row, col) = (row+0.5)*cellSize, (col+0.5)*cellSize
                BigDecimal x = BigDecimal.valueOf(r + 0.5).multiply(cellSize);
                BigDecimal y = BigDecimal.valueOf(c + 0.5).multiply(cellSize);
                String code = String.format("RP-%d-%d", r, c);
                String rpType = type.toRootPointType();

                RootPoint rp = new RootPoint(id, code, x, y, 1, rpType, false);
                repository.saveRootPoint(rp);

                // Generate spot for SHELF cells — code matches UE: S-{row_letter}{col+1}
                if (type.generatesSpot()) {
                    shelfCount++;
                    String spotCode = String.format("S-%c%02d", (char) ('A' + r), c + 1);
                    String aisle = String.valueOf((char) ('A' + r));
                    String section = String.valueOf(c + 1);
                    repository.saveSpotCode(spotCode, aisle, section, 1, id, x, y, BigDecimal.ZERO);
                }
            }
        }

        // Flush root_points before saving edges (FK constraint)
        repository.flush();

        // 5. Generate route_edges between adjacent navigable cells
        int edgeCount = 0;
        int[][] directions = {{0, 1}, {1, 0}}; // right, down (bidirectional covers left, up)

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (pointIds[r][c] == 0) continue;

                for (int[] dir : directions) {
                    int nr = r + dir[0];
                    int nc = c + dir[1];
                    if (nr < rows && nc < cols && pointIds[nr][nc] != 0) {
                        RouteEdge edge = new RouteEdge(
                                pointIds[r][c], pointIds[nr][nc],
                                cellSize, true, BigDecimal.ONE);
                        repository.saveRouteEdge(edge);
                        edgeCount++;
                    }
                }
            }
        }

        log.info("Layout activated: {} root_points, {} edges, {} shelves",
                rows * cols, edgeCount, shelfCount);
    }
}