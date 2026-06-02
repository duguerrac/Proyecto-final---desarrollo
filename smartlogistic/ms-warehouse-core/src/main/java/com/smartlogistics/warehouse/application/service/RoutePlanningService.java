package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RouteEdgeJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RobotJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RootPointJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RouteEdgeJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RobotJpaRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Plans routes through the warehouse using Dijkstra's algorithm
 * on the root_point + route_edge graph.
 *
 * Coordinates in DB match UE5: x = col * cellSize, y = row * cellSize
 * where cellSize = 200 UE units.
 *
 * The planner excludes:
 *  - SHELF cells (static obstacles — the route cannot pass through shelves)
 *  - Cells currently occupied by other robots (dynamic obstacles)
 */
@Service
public class RoutePlanningService {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanningService.class);

    /** Shelf positions in the grid (row, col) based on V9 layout */
    private static final Set<String> SHELF_POSITIONS = Set.of(
            // Row 0: SHELF at cols 2, 4, 6, 8
            "0-2", "0-4", "0-6", "0-8",
            // Row 2: SHELF at cols 1, 3, 5, 7
            "2-1", "2-3", "2-5", "2-7",
            // Row 4: SHELF at cols 2, 4, 6, 8
            "4-2", "4-4", "4-6", "4-8"
    );

    /** Pattern to extract row and col from root point code like RP-R02-C03 */
    private static final Pattern ROW_COL_PATTERN = Pattern.compile("RP-R(\\d+)-C(\\d+)");

    private final RootPointJpaRepository rootPointRepo;
    private final RouteEdgeJpaRepository routeEdgeRepo;
    private final RobotJpaRepository robotRepo;

    /** Cached adjacency list: nodeId → list of (neighborNodeId, distance) */
    private Map<Long, List<double[]>> adjacency = new HashMap<>();
    /** Cached node entities by ID */
    private Map<Long, RootPointJpaEntity> nodesById = new HashMap<>();
    /** Cached node entities by code */
    private Map<String, RootPointJpaEntity> nodesByCode = new HashMap<>();
    /** Set of node IDs that correspond to SHELF cells (not walkable) */
    private Set<Long> shelfNodeIds = new HashSet<>();
    private boolean graphLoaded = false;

    public RoutePlanningService(RootPointJpaRepository rootPointRepo,
                                 RouteEdgeJpaRepository routeEdgeRepo,
                                 RobotJpaRepository robotRepo) {
        this.rootPointRepo = rootPointRepo;
        this.routeEdgeRepo = routeEdgeRepo;
        this.robotRepo = robotRepo;
    }

    /** Check if a root point code corresponds to a SHELF cell in the grid */
    private boolean isShelfPosition(String code) {
        Matcher m = ROW_COL_PATTERN.matcher(code);
        if (m.matches()) {
            int row = Integer.parseInt(m.group(1));
            int col = Integer.parseInt(m.group(2));
            return SHELF_POSITIONS.contains(row + "-" + col);
        }
        return false;
    }

    /** Load or reload the graph from DB, excluding SHELF nodes */
    public synchronized void loadGraph() {
        List<RootPointJpaEntity> points = rootPointRepo.findAllByOrderByIdAsc();
        List<RouteEdgeJpaEntity> edges = routeEdgeRepo.findAllByOrderByIdAsc();

        nodesById.clear();
        nodesByCode.clear();
        adjacency.clear();
        shelfNodeIds.clear();

        // First pass: register all nodes and identify shelf nodes
        for (RootPointJpaEntity p : points) {
            nodesById.put(p.getId(), p);
            nodesByCode.put(p.getCode(), p);

            if (isShelfPosition(p.getCode())) {
                shelfNodeIds.add(p.getId());
                log.debug("SHELF node excluded from navigation: {}", p.getCode());
            } else {
                adjacency.put(p.getId(), new ArrayList<>());
            }
        }

        // Second pass: build edges, skipping any that involve shelf nodes
        int skippedEdges = 0;
        for (RouteEdgeJpaEntity e : edges) {
            // Skip edges where source or target is a shelf node
            if (shelfNodeIds.contains(e.getSourceId()) || shelfNodeIds.contains(e.getTargetId())) {
                skippedEdges++;
                continue;
            }

            double dist = e.getDistance() != null ? e.getDistance().doubleValue() : 200.0;
            // Forward edge
            adjacency.computeIfAbsent(e.getSourceId(), k -> new ArrayList<>())
                    .add(new double[]{e.getTargetId(), dist});
            // Bidirectional: add reverse edge
            if (e.isBidirectional()) {
                adjacency.computeIfAbsent(e.getTargetId(), k -> new ArrayList<>())
                        .add(new double[]{e.getSourceId(), dist});
            }
        }

        graphLoaded = true;
        log.info("Route graph loaded: {} nodes ({} shelf excluded), {} edges ({} skipped)",
                points.size(), shelfNodeIds.size(), edges.size(), skippedEdges);
    }

    /** Ensure graph is loaded before use */
    private void ensureLoaded() {
        if (!graphLoaded) {
            loadGraph();
        }
    }

    /**
     * Get the set of root point codes currently occupied by robots.
     * Queries the DB each time for real-time accuracy.
     */
    private Set<String> getOccupiedLocations() {
        List<RobotJpaEntity> robots = robotRepo.findAll();
        Set<String> occupied = new HashSet<>();
        for (RobotJpaEntity robot : robots) {
            String loc = robot.getCurrentLocation();
            if (loc != null && !loc.isBlank()) {
                occupied.add(loc);
            }
        }
        return occupied;
    }

    /**
     * Find the shortest route between two root points by code.
     * Excludes SHELF nodes (static) and cells occupied by other robots (dynamic).
     * Returns ordered list of root point entities from source to target.
     */
    public List<RootPointJpaEntity> findRoute(String fromCode, String toCode) {
        return findRoute(fromCode, toCode, null);
    }

    /**
     * Find the shortest route between two root points by code,
     * excluding a specific robot's position from the blocked set.
     *
     * @param fromCode Source root point code
     * @param toCode Target root point code
     * @param excludeRobotId Robot ID whose current position should NOT be blocked (the requesting robot)
     */
    public List<RootPointJpaEntity> findRoute(String fromCode, String toCode, String excludeRobotId) {
        ensureLoaded();

        RootPointJpaEntity from = nodesByCode.get(fromCode);
        RootPointJpaEntity to = nodesByCode.get(toCode);

        if (from == null || to == null) {
            log.warn("Cannot find route: from={} to={} — node not found", fromCode, toCode);
            return Collections.emptyList();
        }

        if (from.getId().equals(to.getId())) {
            return List.of(from);
        }

        // Get dynamic obstacles: positions occupied by robots
        Set<String> occupiedLocations = getOccupiedLocations();

        // If this is a robot planning its own route, exclude its current position from blocked set
        if (excludeRobotId != null) {
            RobotJpaEntity requestingRobot = robotRepo.findById(excludeRobotId).orElse(null);
            if (requestingRobot != null && requestingRobot.getCurrentLocation() != null) {
                occupiedLocations.remove(requestingRobot.getCurrentLocation());
            }
        }

        // Build set of blocked node IDs (shelf nodes are already excluded from adjacency)
        Set<Long> blockedByRobots = new HashSet<>();
        for (String loc : occupiedLocations) {
            RootPointJpaEntity occupiedNode = nodesByCode.get(loc);
            if (occupiedNode != null) {
                blockedByRobots.add(occupiedNode.getId());
            }
        }

        log.info("Route planning: {} → {}, occupied locations: {}, blocked node IDs: {}, excludeRobotId: {}",
                fromCode, toCode, occupiedLocations, blockedByRobots, excludeRobotId);

        // Dijkstra
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dist.getOrDefault(a[0], Double.MAX_VALUE)));

        for (Long nodeId : adjacency.keySet()) {
            dist.put(nodeId, Double.MAX_VALUE);
        }
        dist.put(from.getId(), 0.0);
        pq.add(new long[]{from.getId()});

        while (!pq.isEmpty()) {
            long[] curr = pq.poll();
            long u = curr[0];
            if (visited.contains(u)) continue;
            visited.add(u);

            if (u == to.getId()) break; // Found shortest path

            for (double[] neighbor : adjacency.getOrDefault(u, Collections.emptyList())) {
                long v = (long) neighbor[0];
                double w = neighbor[1];

                // Skip statically blocked nodes (shouldn't be in adjacency, but safety check)
                RootPointJpaEntity target = nodesById.get(v);
                if (target != null && target.isBlocked()) continue;

                // Skip nodes occupied by other robots (EXCEPT the destination)
                // The destination is where the robot needs to go, so it must be reachable
                if (blockedByRobots.contains(v) && v != to.getId()) {
                    continue;
                }

                double newDist = dist.get(u) + w;
                if (newDist < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, newDist);
                    prev.put(v, u);
                    pq.add(new long[]{v});
                }
            }
        }

        // Reconstruct path
        if (!prev.containsKey(to.getId()) && !from.getId().equals(to.getId())) {
            log.warn("No route found from {} to {} (blocked by shelves or robots)", fromCode, toCode);
            return Collections.emptyList();
        }

        List<Long> path = new ArrayList<>();
        Long current = to.getId();
        while (current != null) {
            path.add(current);
            current = prev.get(current);
        }
        Collections.reverse(path);

        List<RootPointJpaEntity> route = path.stream()
                .map(nodesById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Route planned: {} → {} ({} steps, distance={})",
                fromCode, toCode, route.size(),
                String.format("%.1f", dist.getOrDefault(to.getId(), 0.0)));

        return route;
    }

    /**
     * Find route from spot code to spot code.
     * Resolves spot → root_point, then runs Dijkstra.
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
            log.warn("Cannot resolve root points for spots: {} → {}", fromSpotCode, toSpotCode);
            return Collections.emptyList();
        }

        return findRoute(from.getCode(), to.getCode(), excludeRobotId);
    }

    /**
     * Resolve a spot code to its associated root point.
     * Maps spots to the nearest CORRIDOR root point (not shelf cells).
     *
     * Grid layout (from V9):
     *   Row 0: ENTRY  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EXIT
     *   Row 1: EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  ← corridor
     *   Row 2: EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  EMPTY
     *   Row 3: EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  ← corridor
     *   Row 4: CHARGE EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY  SHELF  EMPTY
     *   Row 5: CHARGE EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  EMPTY  CHARGE ← corridor
     *
     * Spot → corridor mapping:
     *   Aisle A (Row 2 shelves): accessible from Row 1 (above) or Row 3 (below)
     *   Aisle B (Row 4 shelves): accessible from Row 3 (above) or Row 5 (below)
     */
    private RootPointJpaEntity resolveSpotRootPoint(String spotCode) {
        // Spot → nearest corridor root point mapping
        // Spots in Aisle A (Row 2) → corridor Row 1 (above the shelf)
        // Spots in Aisle B (Row 4) → corridor Row 3 (above the shelf)
        Map<String, String> spotToRootPoint = Map.ofEntries(
                // Aisle A (Row 2 shelves → corridor Row 1 above)
                // SP-A1-01 is at RP-R02-C01 (SHELF) → access from RP-R01-C01 (corridor)
                Map.entry("SP-A1-01", "RP-R01-C01"),
                Map.entry("SP-A1-02", "RP-R01-C03"),
                Map.entry("SP-A1-03", "RP-R01-C05"),
                Map.entry("SP-A1-04", "RP-R01-C07"),
                // Aisle B (Row 4 shelves → corridor Row 3 above)
                // SP-B1-01 is at RP-R04-C02 (SHELF) → access from RP-R03-C02 (corridor)
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
                // Charging stations (Row 4 col 0, Row 5 col 0, Row 5 col 9)
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
            RootPointJpaEntity direct = nodesByCode.get(spotCode);
            // If this is a shelf node, log a warning
            if (direct != null && shelfNodeIds.contains(direct.getId())) {
                log.warn("Direct RP code {} is a SHELF node — routes cannot pass through shelves", spotCode);
            }
            return direct;
        }

        // Fuzzy match: try to find by name pattern
        for (Map.Entry<String, RootPointJpaEntity> entry : nodesByCode.entrySet()) {
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
        return shelfNodeIds.stream()
                .map(id -> nodesById.get(id))
                .filter(Objects::nonNull)
                .map(RootPointJpaEntity::getCode)
                .collect(Collectors.toSet());
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

            // Determine action at this waypoint
            String action = "NAVIGATE";
            if (i == 0) action = "START";
            else if (i == route.size() - 1) action = "ARRIVE";
            wp.put("action", action);

            waypoints.add(wp);
        }
        return waypoints;
    }
}