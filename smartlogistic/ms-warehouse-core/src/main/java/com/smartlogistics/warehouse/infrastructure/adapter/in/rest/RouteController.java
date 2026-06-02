package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.application.dto.CompleteRouteCommand;
import com.smartlogistics.warehouse.application.dto.RoutePlanResponse;
import com.smartlogistics.warehouse.application.port.in.CompleteRouteUseCase;
import com.smartlogistics.warehouse.application.service.RoutePlanningService;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request.CompleteRouteRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final CompleteRouteUseCase completeRoute;
    private final RoutePlanningService routePlanningService;

    public RouteController(CompleteRouteUseCase completeRoute, RoutePlanningService routePlanningService) {
        this.completeRoute = completeRoute;
        this.routePlanningService = routePlanningService;
    }

    @PostMapping("/{id}/complete")
    public RoutePlanResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRouteRequest request) {
        long durationSeconds = request == null ? 1 : request.durationSeconds();
        return completeRoute.complete(new CompleteRouteCommand(id, durationSeconds));
    }

    /**
     * Plan a route between two root points by code.
     * GET /api/routes/plan?from=RP-R00-C00&to=RP-R02-C03
     */
    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> planRoute(
            @RequestParam String from,
            @RequestParam String to) {
        List<RootPointJpaEntity> route = routePlanningService.findRoute(from, to);

        if (route.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from);
        response.put("to", to);
        response.put("stepCount", route.size());
        response.put("waypoints", routePlanningService.routeToWaypoints(route));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/from/{from}/to/{to}")
    public ResponseEntity<Map<String, Object>> routeForSimulation(
            @PathVariable String from,
            @PathVariable String to) {
        List<RootPointJpaEntity> route = routePlanningService.findRouteBetweenSpots(from, to);

        if (route.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<String> pathCodes = route.stream()
                .map(RootPointJpaEntity::getCode)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from);
        response.put("to", to);
        response.put("path", pathCodes);
        response.put("stepCount", route.size());
        response.put("waypoints", routePlanningService.routeToWaypoints(route));

        return ResponseEntity.ok(response);
    }

    /**
     * Plan a route between two spots by code.
     * GET /api/routes/plan/spots?from=ENTRY-01&to=SP-A1-01
     */
    @GetMapping("/plan/spots")
    public ResponseEntity<Map<String, Object>> planRouteBetweenSpots(
            @RequestParam String from,
            @RequestParam String to) {
        List<RootPointJpaEntity> route = routePlanningService.findRouteBetweenSpots(from, to);

        if (route.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from);
        response.put("to", to);
        response.put("stepCount", route.size());
        response.put("waypoints", routePlanningService.routeToWaypoints(route));

        return ResponseEntity.ok(response);
    }

    /**
     * Reload the route graph from DB.
     * POST /api/routes/graph/reload
     */
    @PostMapping("/graph/reload")
    public ResponseEntity<Map<String, String>> reloadGraph() {
        routePlanningService.loadGraph();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }

    /**
     * Get entry/exit/charging points.
     * GET /api/routes/points
     */
    @GetMapping("/points")
    public ResponseEntity<Map<String, Object>> getSpecialPoints() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("entry", routePlanningService.getEntryPoint() != null
                ? pointToMap(routePlanningService.getEntryPoint()) : null);
        response.put("exit", routePlanningService.getExitPoint() != null
                ? pointToMap(routePlanningService.getExitPoint()) : null);
        response.put("chargingStations", routePlanningService.getChargingPoints().stream()
                .map(this::pointToMap).toList());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> pointToMap(RootPointJpaEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.getCode());
        m.put("x", p.getX());
        m.put("y", p.getY());
        m.put("type", p.getType());
        return m;
    }
}
