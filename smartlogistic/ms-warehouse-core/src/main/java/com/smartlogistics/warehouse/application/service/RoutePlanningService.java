package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RouteEdgeJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RootPointJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RouteEdgeJpaRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Plans routes through the warehouse using Dijkstra's algorithm
 * on the root_point + route_edge graph.
 *
 * Coordinates in DB match UE5: x = col * cellSize, y = row * cellSize
 * where cellSize = 200 UE units.
 */
@Service
public class RoutePlanningService {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanningService.class);

    private final RootPointJpaRepository rootPointRepo;
    private final RouteEdgeJpaRepository routeEdgeRepo;

    /** Cached adjacency list: nodeId → list of (neighborNodeId, distance) */
    private Map<Long, List<double[]>> adjacency = new HashMap<>();
    /** Cached node entities by ID */
    private Map<Long, RootPointJpaEntity> nodesById = new HashMap<>();
    /** Cached node entities by code */
    private Map<String, RootPointJpaEntity> nodesByCode = new HashMap<>();
    private boolean graphLoaded = false;

    public RoutePlanningService(RootPointJpaRepository rootPointRepo, RouteEdgeJpaRepository routeEdgeRepo) {
        this.rootPointRepo = rootPointRepo;
        this.routeEdgeRepo = routeEdgeRepo;
    }

    /** Load or reload the graph from DB */
    public synchronized void loadGraph() {
        List<RootPointJpaEntity> points = rootPointRepo.findAllByOrderByIdAsc();
        List<RouteEdgeJpaEntity> edges = routeEdgeRepo.findAllByOrderByIdAsc();

        nodesById.clear();
        nodesByCode.clear();
        adjacency.clear();

        for (RootPointJpaEntity p : points) {
            nodesById.put(p.getId(), p);
            nodesByCode.put(p.getCode(), p);
            adjacency.put(p.getId(), new ArrayList<>());
        }

        for (RouteEdgeJpaEntity e : edges) {
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
        log.info("Route graph loaded: {} nodes, {} edges", points.size(), edges.size());
    }

    /** Ensure graph is loaded before use */
    private void ensureLoaded() {
        if (!graphLoaded) {
            loadGraph();
        }
    }

    /**
     * Find the shortest route between two root points by code.
     * Returns ordered list of root point entities from source to target.
     */
    public List<RootPointJpaEntity> findRoute(String fromCode, String toCode) {
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

        // Dijkstra
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dist.getOrDefault(a[0], Double.MAX_VALUE)));

        for (Long nodeId : nodesById.keySet()) {
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
                // Skip blocked nodes
                RootPointJpaEntity target = nodesById.get(v);
                if (target != null && target.isBlocked()) continue;

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
            log.warn("No route found from {} to {}", fromCode, toCode);
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
        ensureLoaded();

        // Spots have root_point references; we look up by spot code pattern
        // The spot code maps to a root point: SP-A1-01 → RP-R02-C01 etc.
        // For simplicity, we look for root points near the spot
        // In the future, this should query the spot table for root_point_id
        RootPointJpaEntity from = resolveSpotRootPoint(fromSpotCode);
        RootPointJpaEntity to = resolveSpotRootPoint(toSpotCode);

        if (from == null || to == null) {
            log.warn("Cannot resolve root points for spots: {} → {}", fromSpotCode, toSpotCode);
            return Collections.emptyList();
        }

        return findRoute(from.getCode(), to.getCode());
    }

    /**
     * Resolve a spot code to its associated root point.
     * Mapping for 8×10 grid (rows 0-7, cols 0-9):
     *   Row 0 (y=1400): SPAWN corridor — DOCK/ENTRY points
     *   Row 1 (y=1200): SHELF row (Aisle A)
     *   Row 2 (y=1000): Internal corridor
     *   Row 3 (y=800):  SHELF row (Aisle B)
     *   Row 4 (y=600):  Internal corridor
     *   Row 5 (y=400):  SHELF row (Aisle C)
     *   Row 6 (y=200):  Internal corridor
     *   Row 7 (y=0):    CHARGING / START / EXIT
     */
    private RootPointJpaEntity resolveSpotRootPoint(String spotCode) {
        // Standard spot → root point mapping (using ofEntries because >10 pairs)
        Map<String, String> spotToRootPoint = Map.ofEntries(
                // Shelf spots: SP maps to the nearest corridor root point (one row below the shelf)
                // Aisle A (Row 1 shelves → accessible from Row 2 corridor)
                Map.entry("SP-A1-01", "RP-R02-C01"),
                Map.entry("SP-A1-02", "RP-R02-C03"),
                Map.entry("SP-A1-03", "RP-R02-C06"),
                Map.entry("SP-A1-04", "RP-R02-C08"),
                // Aisle B (Row 3 shelves → accessible from Row 2 or Row 4 corridor)
                Map.entry("SP-B1-01", "RP-R02-C01"),
                Map.entry("SP-B1-02", "RP-R02-C03"),
                Map.entry("SP-B1-03", "RP-R02-C06"),
                Map.entry("SP-B1-04", "RP-R02-C08"),
                // Aisle C (Row 5 shelves → accessible from Row 6 corridor)
                Map.entry("SP-C1-01", "RP-R06-C01"),
                Map.entry("SP-C1-02", "RP-R06-C03"),
                Map.entry("SP-C1-03", "RP-R06-C06"),
                Map.entry("SP-C1-04", "RP-R06-C08"),
                // Entry/Exit/Dock points
                Map.entry("ENTRY-01",  "RP-R00-C00"),
                Map.entry("ENTRY-02",  "RP-R00-C09"),
                Map.entry("EXIT-01",   "RP-R07-C09"),
                Map.entry("RECV-01",   "RP-R00-C00"),
                Map.entry("DOCK-01",   "RP-R00-C00"),
                Map.entry("DOCK-02",   "RP-R00-C09"),
                Map.entry("DOCK-03",   "RP-R00-C04"),
                Map.entry("DOCK-04",   "RP-R00-C05"),
                // Charging stations (Row 7)
                Map.entry("CHARGE-01", "RP-R07-C00"),
                Map.entry("CHARGE-02", "RP-R07-C01"),
                // Start point
                Map.entry("START-01",  "RP-R07-C08")
        );

        String rpCode = spotToRootPoint.get(spotCode);
        if (rpCode != null) {
            return nodesByCode.get(rpCode);
        }

        // If not found in map, try the root_point code directly
        if (spotCode.startsWith("RP-")) {
            return nodesByCode.get(spotCode);
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
                .filter(p -> "CHARGING".equals(p.getType()))
                .collect(Collectors.toList());
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