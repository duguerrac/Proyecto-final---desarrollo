package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.domain.exception.RoutePlanningException;
import com.smartlogistics.warehouse.domain.model.RootPoint;
import com.smartlogistics.warehouse.domain.model.RouteEdge;
import com.smartlogistics.warehouse.domain.model.RouteStep;
import com.smartlogistics.warehouse.domain.model.RouteStepAction;
import java.math.BigDecimal;
import java.util.*;

public class DijkstraRouteCalculator {
    public RouteCalculation calculate(List<RootPoint> points, List<RouteEdge> edges, String startCode, List<String> pickupCodes, String exitCode) {
        Map<String, RootPoint> byCode = new HashMap<>();
        Map<Long, RootPoint> byId = new HashMap<>();
        for (RootPoint point : points) {
            // Exclude blocked nodes AND SHELF-type nodes (shelves are not walkable)
            if (!point.blocked() && !"SHELF".equals(point.type())) {
                byCode.put(point.code(), point);
                byId.put(point.id(), point);
            }
        }

        List<String> waypoints = new ArrayList<>();
        waypoints.add(startCode);
        pickupCodes.stream().distinct().filter(code -> !code.equals(startCode)).forEach(waypoints::add);
        if (!waypoints.get(waypoints.size() - 1).equals(exitCode)) {
            waypoints.add(exitCode);
        }

        List<RouteStep> steps = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Segment segment = shortestPath(byId, byCode.get(waypoints.get(i)), byCode.get(waypoints.get(i + 1)), edges);
            total = total.add(segment.distance());
            for (RootPoint point : segment.points()) {
                if (!steps.isEmpty() && Objects.equals(steps.get(steps.size() - 1).rootPointId(), point.id())) {
                    continue;
                }
                RouteStepAction action = point.code().equals(exitCode) ? RouteStepAction.EXIT : RouteStepAction.NAVIGATE;
                steps.add(new RouteStep(steps.size() + 1, point.id(), point.code(), action));
            }
        }

        Set<String> pickupSet = new HashSet<>(pickupCodes);
        steps = steps.stream()
                .map(step -> pickupSet.contains(step.rootPointCode())
                        ? new RouteStep(step.sequence(), step.rootPointId(), step.rootPointCode(), RouteStepAction.PICKUP)
                        : step)
                .toList();
        return new RouteCalculation(total, steps);
    }

    private Segment shortestPath(Map<Long, RootPoint> points, RootPoint start, RootPoint target, List<RouteEdge> edges) {
        if (start == null || target == null) {
            throw new RoutePlanningException("Start or target root point does not exist or is blocked");
        }
        Map<Long, List<Neighbor>> graph = new HashMap<>();
        for (RouteEdge edge : edges) {
            // Skip edges where source or target is excluded (blocked or SHELF)
            if (!points.containsKey(edge.sourceId()) || !points.containsKey(edge.targetId())) {
                continue;
            }
            graph.computeIfAbsent(edge.sourceId(), ignored -> new ArrayList<>()).add(new Neighbor(edge.targetId(), edge.weight()));
            if (edge.bidirectional()) {
                graph.computeIfAbsent(edge.targetId(), ignored -> new ArrayList<>()).add(new Neighbor(edge.sourceId(), edge.weight()));
            }
        }

        Map<Long, BigDecimal> distances = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparing(NodeDistance::distance));
        distances.put(start.id(), BigDecimal.ZERO);
        queue.add(new NodeDistance(start.id(), BigDecimal.ZERO));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (current.distance().compareTo(distances.getOrDefault(current.id(), new BigDecimal("999999999"))) > 0) {
                continue;
            }
            if (current.id().equals(target.id())) {
                break;
            }
            for (Neighbor neighbor : graph.getOrDefault(current.id(), List.of())) {
                BigDecimal candidate = current.distance().add(neighbor.weight());
                if (candidate.compareTo(distances.getOrDefault(neighbor.id(), new BigDecimal("999999999"))) < 0) {
                    distances.put(neighbor.id(), candidate);
                    previous.put(neighbor.id(), current.id());
                    queue.add(new NodeDistance(neighbor.id(), candidate));
                }
            }
        }

        if (!distances.containsKey(target.id())) {
            throw new RoutePlanningException("No route available between " + start.code() + " and " + target.code());
        }

        LinkedList<RootPoint> path = new LinkedList<>();
        Long cursor = target.id();
        while (cursor != null) {
            path.addFirst(points.get(cursor));
            cursor = previous.get(cursor);
        }
        return new Segment(distances.get(target.id()), path);
    }

    public record RouteCalculation(BigDecimal totalDistance, List<RouteStep> steps) {
    }

    private record Segment(BigDecimal distance, List<RootPoint> points) {
    }

    private record Neighbor(Long id, BigDecimal weight) {
    }

    private record NodeDistance(Long id, BigDecimal distance) {
    }
}
