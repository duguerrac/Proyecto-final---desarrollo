package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.mapper;

import com.smartlogistics.warehouse.domain.model.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.*;
import java.time.Instant;
import java.util.List;

public class WarehouseJpaMapper {
    public InventoryItem toDomain(InventoryItemJpaEntity entity) {
        return new InventoryItem(entity.getId(), entity.getSku(), entity.getName(), entity.isFragile(), entity.getDefaultSpeedLimit());
    }

    public RootPoint toDomain(RootPointJpaEntity entity) {
        return new RootPoint(entity.getId(), entity.getCode(), entity.getX(), entity.getY(), entity.getZLevel(), entity.getType(), entity.isBlocked());
    }

    public RouteEdge toDomain(RouteEdgeJpaEntity entity) {
        return new RouteEdge(entity.getSourceId(), entity.getTargetId(), entity.getDistance(), entity.isBidirectional(), entity.getWeight());
    }

    public DispatchOrder toDomain(DispatchOrderJpaEntity entity, List<OrderItem> items) {
        return new DispatchOrder(
                entity.getId(),
                entity.getCreatedAt() == null ? Instant.now() : entity.getCreatedAt(),
                entity.getPriority(),
                DispatchOrderStatus.valueOf(entity.getStatus()),
                entity.getAssignedRobotId(),
                items
        );
    }

    public RouteStep toDomain(RouteStepJpaEntity entity, String rootPointCode) {
        return new RouteStep(entity.getSequence(), entity.getRootPointId(), rootPointCode, RouteStepAction.valueOf(entity.getAction()));
    }

    public RoutePlan toDomain(RoutePlanJpaEntity entity, List<RouteStep> steps) {
        return new RoutePlan(
                entity.getId(),
                entity.getOrderId(),
                entity.getRobotId(),
                RoutePlanStatus.valueOf(entity.getStatus()),
                entity.getTotalDistance(),
                entity.getSpeedFactor(),
                entity.getCreatedAt() == null ? Instant.now() : entity.getCreatedAt(),
                steps
        );
    }

    public long longValue(Object value) {
        return ((Number) value).longValue();
    }

    public int intValue(Object value) {
        return ((Number) value).intValue();
    }
}
