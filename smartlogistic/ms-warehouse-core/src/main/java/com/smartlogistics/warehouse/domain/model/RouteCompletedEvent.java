package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteCompletedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long orderId,
        String robotId,
        String warehouseId,
        boolean fragileItems,
        BigDecimal totalDistanceMeters,
        long durationSeconds,
        List<PathPoint> path
) {
    public static final String TYPE = "route.completed";

    public record PathPoint(String rootPointId, BigDecimal x, BigDecimal y) {
    }
}
