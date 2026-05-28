package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.application.dto.CompleteRouteCommand;
import com.smartlogistics.warehouse.application.dto.RoutePlanResponse;
import com.smartlogistics.warehouse.application.port.in.CompleteRouteUseCase;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request.CompleteRouteRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final CompleteRouteUseCase completeRoute;

    public RouteController(CompleteRouteUseCase completeRoute) {
        this.completeRoute = completeRoute;
    }

    @PostMapping("/{id}/complete")
    public RoutePlanResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRouteRequest request) {
        long durationSeconds = request == null ? 1 : request.durationSeconds();
        return completeRoute.complete(new CompleteRouteCommand(id, durationSeconds));
    }
}
