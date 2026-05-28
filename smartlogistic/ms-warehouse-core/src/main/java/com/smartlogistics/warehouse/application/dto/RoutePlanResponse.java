package com.smartlogistics.warehouse.application.dto;

import com.smartlogistics.warehouse.domain.model.RoutePlan;
import java.math.BigDecimal;
import java.util.List;

public record RoutePlanResponse(Long id, Long orderId, String robotId, String status, BigDecimal totalDistance, BigDecimal speedFactor, List<RouteStepResponse> steps) {
    public static RoutePlanResponse from(RoutePlan routePlan) {
        return new RoutePlanResponse(
                routePlan.id(),
                routePlan.orderId(),
                routePlan.robotId(),
                routePlan.status().name(),
                routePlan.totalDistance(),
                routePlan.speedFactor(),
                routePlan.steps().stream()
                        .map(step -> new RouteStepResponse(step.sequence(), step.rootPointId(), step.rootPointCode(), step.action().name()))
                        .toList()
        );
    }

    public record RouteStepResponse(int sequence, Long rootPointId, String rootPointCode, String action) {
    }
}
