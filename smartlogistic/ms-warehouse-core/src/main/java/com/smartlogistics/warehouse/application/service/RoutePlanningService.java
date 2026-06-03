package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RobotJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RootPointJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RobotJpaRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Plans routes through the warehouse using Dijkstra's algorithm
 * on an in-memory grid where each cell is marked as:
 *   AVAILABLE   → robot can walk through
 *   SHELF       → static obstacle (shelf), permanently blocked
 *   ROBOT_OCCUPIED → dynamic obstacle, blocked until robot moves
 *
 * The grid is the single source of truth for pathfinding.
 * No route edges from the DB are used — pathfinding works directly
 * on the 2D grid using 4-directional adjacency.
 *
 * Coordinates: x = col * cellSize, y = row * cellSize
 * where cellSize = 200 UE units.
 */
@Service
public class RoutePlanningService {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanningService.class);

    /** Pattern to extract row and col from root point code like RP-R02-C03 */
    private static final Pattern ROW_COL_PATTERN = Pattern.compile("RP-R(\\d+)-C(\\d+)");

    private final RootPointJpaRepository rootPointRepo;
    private final RobotJpaRepository robotRepo;

    /** In-memory grid: the single source of truth for what cells are walkable */
    private WarehouseGrid grid;
    /** Cached node entities by code */
    private Map<String, RootPointJpaEntity> nodesByCode = new HashMap<>();
    /** Cached node entities by (row, col) key like "2-3" */
    private Map<String, RootPointJpaEntity> nodesByRowCol = new HashMap<>();
    private boolean gridLoaded = false;

    public RoutePlanningService(RootPointJpaRepository rootPointRepo,
                                 RobotJpaRepository robotRepo) {
        this.rootPointRepo = rootPointRepo;
        this.robotRepo = robotRepo;
    }

    /** Parse row from a root point code like RP-R02-C03 → 2 */
    private int parseRow(String code) {
        Matcher m = ROW_COL_PATTERN.matcher(code);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Parse col from a root point code like RP-R02-C03 → 3 */
    private int parseCol(String code) {
        Matcher m = ROW_COL_PATTERN.matcher(code);
        return m.matches() ? Integer.parseInt(m.group(2)) : -1;
    }

    /** Build the row-col key from a root point code */
    private String rowColKey(String code) {
        int row = parseRow(code);
        int col = parseCol(code);
        return row + "-" + col;
    }

    /**
     * Load or reload the grid from DB.
     * Builds the in-memory grid from root_points, marking SHELF cells as blocked.
     */
    public synchronized void loadGrid() {
        List<RootPointJpaEntity> points = rootPointRepo.findAllByOrderByIdAsc();

        nodesByCode.clear();
        nodesByRowCol.clear();

        // Determine grid dimensions from the data
        int maxRow = 0, maxCol = 0;
        for (RootPointJpaEntity p : points) {
            int row = parseRow(p.getCode());
            int col = parseCol(p.getCode());
            if (row > maxRow) maxRow = row;
            if (col > maxCol) maxCol = col;
        }
        int rows = maxRow + 1;
        int cols = maxCol + 1;

        // Create grid (all cells start as AVAILABLE)
        grid = new WarehouseGrid(rows, cols, 200.0);

        // Mark cells based on root_point data
        int shelfCount = 0;
        for (RootPointJpaEntity p : points) {
            int row = parseRow(p.getCode());
            int col = parseCol(p.getCode());
            if (row < 0 || col < 0) continue;

            nodesByCode.put(p.getCode(), p);
            nodesByRowCol.put(row + "-" + col, p);

            // A cell is a shelf if: type="SHELF" OR blocked=true OR it's a known shelf position
            if ("SHELF".equals(p.getType()) || p.isBlocked()) {
                grid.setShelf(row, col);
                shelfCount++;
                log.debug("SHELF cell marked: {} (type={}, blocked={})", p.getCode(), p.getType(), p.isBlocked());
            }
        }

        // Apply robot positions as dynamic obstacles
        updateRobotPositions();

        gridLoaded = true;
        log.info("Warehouse grid loaded: {}x{} ({} shelf cells marked)", rows, cols, shelfCount);
        log.debug("\n{}", grid.toVisualString());
    }

    /**
     * Update robot positions on the grid as dynamic obstacles.
     * Call this whenever robot locations change.
     */
    public synchronized void updateRobotPositions() {
        if (grid == null) return;

        // First: clear all ROBOT_OCCUPIED cells back to AVAILABLE
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                grid.clearRobotOccupied(r, c);
            }
        }

        // Then: mark current robot positions
        List<RobotJpaEntity> robots = robotRepo.findAll();
        for (RobotJpaEntity robot : robots) {
            String loc = robot.getCurrentLocation();
            if (loc == null || loc.isBlank()) continue;
            int row = parseRow(loc);
            int col = parseCol(loc);
            if (row >= 0 && col >= 0) {
                grid.setRobotOccupied(row, col);
                log.debug("Robot {} at {} → grid[{}][{}] = ROBOT_OCCUPIED", robot.getId(), loc, row, col);
            }
        }
    }

    /** Ensure grid is loaded before use */
    private void ensureLoaded() {
        if (!gridLoaded) {
            loadGrid();
        }
    }

    /**
     * Find the shortest route between two root points by code.
     * Uses Dijkstra on the in-memory grid.
     */
    public List<RootPointJpaEntity> findRoute(String fromCode, String toCode) {
        return findRoute(fromCode, toCode, null);
    }

    /**
     * Find the shortest route between two root points by code,
     * excluding a specific robot's position from the blocked set.
     */
    public List<RootPointJpaEntity> findRoute(String fromCode, String toCode, String excludeRobotId) {
        ensureLoaded();

        // Refresh robot positions for real-time accuracy
        updateRobotPositions();

        // If a robot is planning its own route, clear its current position from the grid
        int excludeRow = -1, excludeCol = -1;
        if (excludeRobotId != null) {
            RobotJpaEntity requestingRobot = robotRepo.findById(excludeRobotId).orElse(null);
            if (requestingRobot != null && requestingRobot.getCurrentLocation() != null) {
                excludeRow = parseRow(requestingRobot.getCurrentLocation());
                excludeCol = parseCol(requestingRobot.getCurrentLocation());
                // Temporarily clear this robot's position so it doesn't block itself
                grid.clearRobotOccupied(excludeRow, excludeCol);
            }
        }

        try {
            int fromRow = parseRow(fromCode);
            int fromCol = parseCol(fromCode);
            int toRow = parseRow(toCode);
            int toCol = parseCol(toCode);

            if (fromRow < 0 || fromCol < 0 || toRow < 0 || toCol < 0) {
                log.warn("Cannot parse route codes: from={} to={}", fromCode, toCode);
                return Collections.emptyList();
            }

            // If source is a shelf, find nearest available cell
            if (grid.isShelf(fromRow, fromCol)) {
                int[] adj = findAdjacentAvailableCell(fromRow, fromCol);
                if (adj != null) {
                    log.info("Source {} is SHELF → rerouted to R{}-C{}", fromCode, adj[0], adj[1]);
                    fromRow = adj[0];
                    fromCol = adj[1];
                }
            }

            // If destination is a shelf, find nearest available cell (one block away)
            if (grid.isShelf(toRow, toCol)) {
                int[] adj = findAdjacentAvailableCell(toRow, toCol);
                if (adj != null) {
                    log.info("Target {} is SHELF → rerouted to R{}-C{}", toCode, adj[0], adj[1]);
                    toRow = adj[0];
                    toCol = adj[1];
                }
            }

            if (fromRow == toRow && fromCol == toCol) {
                RootPointJpaEntity point = nodesByRowCol.get(fromRow + "-" + fromCol);
                return point != null ? List.of(point) : Collections.emptyList();
            }

            // Dijkstra on the grid
            String startKey = fromRow + "-" + fromCol;
            String endKey = toRow + "-" + toCol;

            Map<String, Double> dist = new HashMap<>();
            Map<String, String> prev = new HashMap<>();
            Set<String> visited = new HashSet<>();
            // Priority queue: (row, col) sorted by distance
            PriorityQueue<int[]> pq = new PriorityQueue<>(
                    Comparator.comparingDouble(a -> dist.getOrDefault(a[0] + "-" + a[1], Double.MAX_VALUE)));

            dist.put(startKey, 0.0);
            pq.add(new int[]{fromRow, fromCol});

            while (!pq.isEmpty()) {
                int[] curr = pq.poll();
                int r = curr[0], c = curr[1];
                String key = r + "-" + c;

                if (visited.contains(key)) continue;
                visited.add(key);

                if (r == toRow && c == toCol) break;

                // Get walkable neighbors from the grid
                for (int[] neighbor : grid.getWalkableNeighbors(r, c, true, toRow, toCol)) {
                    int nr = neighbor[0], nc = neighbor[1];
                    String nKey = nr + "-" + nc;

                    double newDist = dist.get(key) + grid.getCellSize();
                    if (newDist < dist.getOrDefault(nKey, Double.MAX_VALUE)) {
                        dist.put(nKey, newDist);
                        prev.put(nKey, key);
                        pq.add(new int[]{nr, nc});
                    }
                }
            }

            // Reconstruct path
            if (!prev.containsKey(endKey) && !(fromRow == toRow && fromCol == toCol)) {
                log.warn("No route found from {} to {} (blocked by shelves or robots)", fromCode, toCode);
                return Collections.emptyList();
            }

            List<String> pathKeys = new ArrayList<>();
            String current = endKey;
            while (current != null) {
                pathKeys.add(current);
                current = prev.get(current);
            }
            Collections.reverse(pathKeys);

            List<RootPointJpaEntity> route = pathKeys.stream()
                    .map(nodesByRowCol::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Route planned: {} → {} ({} steps, distance={})",
                    fromCode, toCode, route.size(),
                    String.format("%.1f", dist.getOrDefault(endKey, 0.0)));

            return route;
        } finally {
            // Restore the excluded robot's position on the grid
            if (excludeRow >= 0 && excludeCol >= 0) {
                grid.setRobotOccupied(excludeRow, excludeCol);
            }
        }
    }

    /**
     * Find the nearest AVAILABLE cell adjacent to a shelf cell.
     * This ensures the robot stops one block next to the shelf,
     * never inside it.
     */
    private int[] findAdjacentAvailableCell(int shelfRow, int shelfCol) {
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            int nr = shelfRow + offset[0];
            int nc = shelfCol + offset[1];
            if (grid.isAvailable(nr, nc)) {
                return new int[]{nr, nc};
            }
        }
        // No adjacent available cell — search wider (BFS)
        return findNearestAvailableCellBFS(shelfRow, shelfCol);
    }

    /** BFS fallback to find nearest available cell */
    private int[] findNearestAvailableCellBFS(int startRow, int startCol) {
        Set<String> visited = new HashSet<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startRow, startCol});
        visited.add(startRow + "-" + startCol);

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int r = curr[0], c = curr[1];

            if (grid.isAvailable(r, c)) {
                return new int[]{r, c};
            }

            int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dir : dirs) {
                int nr = r + dir[0], nc = c + dir[1];
                String key = nr + "-" + nc;
                if (!visited.contains(key) && nr >= 0 && nr < grid.getRows() && nc >= 0 && nc < grid.getCols()) {
                    visited.add(key);
                    queue.add(new int[]{nr, nc});
                }
            }
        }
        return null;
    }

    /**
     * Find route from spot code to spot code.
     * Resolves spot → root_point, then runs Dijkstra on the grid.
     */
    public List<RootPointJpaEntity> findRouteBetweenSpots(String fromSpotCode, String toSpotCode) {
        return findRouteBetweenSpots(fromSpotCode, toSpotCode, null);
    }

    /**
     * Find route from spot code to spot code, excluding a specific robot's position.
     */
    public List<RootPointJpaEntity> findRouteBetweenSpots(String fromSpotCode, String toSpotCode, String excludeRobotId) {
        ensureLoaded();

        RootPointJpaEntity from = resolveSpotRootPoint(fromSpotCode);
        RootPointJpaEntity to = resolveSpotRootPoint(toSpotCode);

        if (from == null || to == null) {
            log.warn("Cannot resolve root points for spots: {} -> {}", fromSpotCode, toSpotCode);
            return Collections.emptyList();
        }

        return findRoute(from.getCode(), to.getCode(), excludeRobotId);
    }

    /**
     * Resolve a spot code to its associated root point.
     * Maps shelf spots to the nearest CORRIDOR root point (one block away from the shelf).
     */
    private RootPointJpaEntity resolveSpotRootPoint(String spotCode) {
        // Spot → corridor root point mapping (shelf spots → adjacent corridor cell)
        Map<String, String> spotToRootPoint = Map.ofEntries(
                // Aisle A (Row 2 shelves → corridor Row 1 above)
                Map.entry("SP-A1-01", "RP-R01-C01"),
                Map.entry("SP-A1-02", "RP-R01-C03"),
                Map.entry("SP-A1-03", "RP-R01-C05"),
                Map.entry("SP-A1-04", "RP-R01-C07"),
                // Aisle B (Row 4 shelves → corridor Row 3 above)
                Map.entry("SP-B1-01", "RP-R03-C02"),
                Map.entry("SP-B1-02", "RP-R03-C04"),
                Map.entry("SP-B1-03", "RP-R03-C06"),
                Map.entry("SP-B1-04", "RP-R03-C08"),
                // Entry/Exit/Dock points
                Map.entry("ENTRY-01",  "RP-R00-C00"),
                Map.entry("ENTRY-02",  "RP-R00-C09"),
                Map.entry("EXIT-01",   "RP-R05-C09"),
                Map.entry("RECV-01",   "RP-R00-C00"),
                Map.entry("DOCK-01",   "RP-R00-C00"),
                Map.entry("DOCK-02",   "RP-R00-C09"),
                Map.entry("DOCK-03",   "RP-R00-C04"),
                Map.entry("DOCK-04",   "RP-R00-C05"),
                // Charging stations
                Map.entry("CHARGE-01", "RP-R04-C00"),
                Map.entry("CHARGE-02", "RP-R05-C00"),
                Map.entry("CHARGE-03", "RP-R05-C09"),
                // Start point
                Map.entry("START-01",  "RP-R00-C00")
        );

        String rpCode = spotToRootPoint.get(spotCode);
        if (rpCode != null) {
            return nodesByCode.get(rpCode);
        }

        // If not found in map, try the root_point code directly
        if (spotCode.startsWith("RP-")) {
            return nodesByCode.get(spotCode);
        }

        // Fuzzy match: try to find by name pattern (exclude shelf nodes)
        for (Map.Entry<String, RootPointJpaEntity> entry : nodesByCode.entrySet()) {
            int row = parseRow(entry.getKey());
            int col = parseCol(entry.getKey());
            if (row >= 0 && col >= 0 && grid != null && grid.isShelf(row, col)) continue;
            if (entry.getKey().contains(spotCode) || spotCode.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /** Get a root point by its code */
    public RootPointJpaEntity getRootPoint(String code) {
        ensureLoaded();
        return nodesByCode.get(code);
    }

    /** Get the entry point (START type) */
    public RootPointJpaEntity getEntryPoint() {
        ensureLoaded();
        return nodesByCode.values().stream()
                .filter(p -> "START".equals(p.getType()))
                .findFirst()
                .orElse(null);
    }

    /** Get the exit point (EXIT type) */
    public RootPointJpaEntity getExitPoint() {
        ensureLoaded();
        return nodesByCode.values().stream()
                .filter(p -> "EXIT".equals(p.getType()))
                .findFirst()
                .orElse(null);
    }

    /** Get all charging points */
    public List<RootPointJpaEntity> getChargingPoints() {
        ensureLoaded();
        return nodesByCode.values().stream()
                .filter(p -> "CHARGE".equals(p.getType()))
                .collect(Collectors.toList());
    }

    /** Get all shelf node codes (for debugging / UI) */
    public Set<String> getShelfNodeCodes() {
        ensureLoaded();
        Set<String> shelfCodes = new HashSet<>();
        for (Map.Entry<String, RootPointJpaEntity> entry : nodesByCode.entrySet()) {
            int row = parseRow(entry.getKey());
            int col = parseCol(entry.getKey());
            if (row >= 0 && col >= 0 && grid.isShelf(row, col)) {
                shelfCodes.add(entry.getKey());
            }
        }
        return shelfCodes;
    }

    /** Convert a route to waypoint DTOs for transmission */
    public List<Map<String, Object>> routeToWaypoints(List<RootPointJpaEntity> route) {
        List<Map<String, Object>> waypoints = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            RootPointJpaEntity p = route.get(i);
            Map<String, Object> wp = new LinkedHashMap<>();
            wp.put("sequence", i);
            wp.put("code", p.getCode());
            wp.put("x", p.getX());
            wp.put("y", p.getY());
            wp.put("type", p.getType());

            String action = "NAVIGATE";
            if (i == 0) action = "START";
            else if (i == route.size() - 1) action = "ARRIVE";
            wp.put("action", action);

            waypoints.add(wp);
        }
        return waypoints;
    }

    /** Get the current grid state (for debugging) */
    public String getGridState() {
        ensureLoaded();
        return grid.toVisualString();
    }

    /** Keep loadGraph() as alias for loadGrid() for backward compatibility */
    public synchronized void loadGraph() {
        loadGrid();
    }
}
