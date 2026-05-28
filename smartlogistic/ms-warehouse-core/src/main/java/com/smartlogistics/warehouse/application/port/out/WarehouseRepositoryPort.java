package com.smartlogistics.warehouse.application.port.out;

import com.smartlogistics.warehouse.domain.model.*;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepositoryPort {
    Optional<InventoryItem> findInventoryBySku(String sku);
    DispatchOrder createOrder(String priority, List<OrderItem> items);
    Optional<DispatchOrder> findOrder(Long orderId);
    void saveOrder(DispatchOrder order);
    List<SpotItem> findSpotsBySku(String sku);
    List<RootPoint> findPickingRootPointsForOrder(Long orderId);
    List<RootPoint> findRootPoints();
    List<RouteEdge> findRouteEdges();
    RoutePlan createRoutePlan(Long orderId, String robotId, java.math.BigDecimal totalDistance, java.math.BigDecimal speedFactor, List<RouteStep> steps);
    Optional<RoutePlan> findRoutePlan(Long routePlanId);
    void saveRoutePlan(RoutePlan routePlan);
    void reserveStockForOrder(Long orderId);
    void decrementStockForOrder(Long orderId);
    List<RootPoint> findRootPointsByIds(List<Long> ids);
}
