package com.smartlogistics.warehouse.domain.model;

import com.smartlogistics.warehouse.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class RoutePlan {
    private final Long id;
    private final Long orderId;
    private final String robotId;
    private final BigDecimal totalDistance;
    private final BigDecimal speedFactor;
    private final Instant createdAt;
    private final List<RouteStep> steps;
    private RoutePlanStatus status;

    public RoutePlan(Long id, Long orderId, String robotId, RoutePlanStatus status, BigDecimal totalDistance, BigDecimal speedFactor, Instant createdAt, List<RouteStep> steps) {
        this.id = id;
        this.orderId = orderId;
        this.robotId = robotId;
        this.status = status;
        this.totalDistance = totalDistance;
        this.speedFactor = speedFactor;
        this.createdAt = createdAt;
        this.steps = List.copyOf(steps);
    }

    public void complete() {
        if (status == RoutePlanStatus.COMPLETED) {
            throw new BusinessException("Route is already completed");
        }
        this.status = RoutePlanStatus.COMPLETED;
    }

    public Long id() { return id; }
    public Long orderId() { return orderId; }
    public String robotId() { return robotId; }
    public RoutePlanStatus status() { return status; }
    public BigDecimal totalDistance() { return totalDistance; }
    public BigDecimal speedFactor() { return speedFactor; }
    public Instant createdAt() { return createdAt; }
    public List<RouteStep> steps() { return steps; }
}
