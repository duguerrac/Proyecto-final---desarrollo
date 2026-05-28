package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres;

import com.smartlogistics.warehouse.application.port.out.WarehouseRepositoryPort;
import com.smartlogistics.warehouse.domain.exception.InsufficientStockException;
import com.smartlogistics.warehouse.domain.model.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.mapper.WarehouseJpaMapper;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresWarehouseRepositoryAdapter implements WarehouseRepositoryPort {
    private final InventoryItemJpaRepository inventoryItemRepository;
    private final DispatchOrderJpaRepository dispatchOrderRepository;
    private final OrderItemJpaRepository orderItemRepository;
    private final SpotItemJpaRepository spotItemRepository;
    private final RootPointJpaRepository rootPointRepository;
    private final RouteEdgeJpaRepository routeEdgeRepository;
    private final RoutePlanJpaRepository routePlanRepository;
    private final RouteStepJpaRepository routeStepRepository;
    private final WarehouseJpaMapper mapper = new WarehouseJpaMapper();

    public PostgresWarehouseRepositoryAdapter(
            InventoryItemJpaRepository inventoryItemRepository,
            DispatchOrderJpaRepository dispatchOrderRepository,
            OrderItemJpaRepository orderItemRepository,
            SpotItemJpaRepository spotItemRepository,
            RootPointJpaRepository rootPointRepository,
            RouteEdgeJpaRepository routeEdgeRepository,
            RoutePlanJpaRepository routePlanRepository,
            RouteStepJpaRepository routeStepRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.dispatchOrderRepository = dispatchOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.spotItemRepository = spotItemRepository;
        this.rootPointRepository = rootPointRepository;
        this.routeEdgeRepository = routeEdgeRepository;
        this.routePlanRepository = routePlanRepository;
        this.routeStepRepository = routeStepRepository;
    }

    @Override
    public Optional<InventoryItem> findInventoryBySku(String sku) {
        return inventoryItemRepository.findBySku(sku).map(mapper::toDomain);
    }

    @Override
    public DispatchOrder createOrder(String priority, List<OrderItem> items) {
        DispatchOrderJpaEntity order = new DispatchOrderJpaEntity();
        order.setStatus(DispatchOrderStatus.PENDING.name());
        order.setPriority(priority);
        DispatchOrderJpaEntity savedOrder = dispatchOrderRepository.saveAndFlush(order);

        for (OrderItem item : items) {
            OrderItemJpaEntity orderItem = new OrderItemJpaEntity();
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setItemId(item.itemId());
            orderItem.setRequestedQuantity(item.quantity());
            orderItemRepository.save(orderItem);
        }
        return findOrder(savedOrder.getId()).orElseThrow();
    }

    @Override
    public Optional<DispatchOrder> findOrder(Long orderId) {
        return dispatchOrderRepository.findById(orderId)
                .map(order -> mapper.toDomain(order, findDomainOrderItems(orderId)));
    }

    @Override
    public void saveOrder(DispatchOrder order) {
        DispatchOrderJpaEntity entity = dispatchOrderRepository.findById(order.id()).orElseThrow();
        entity.setStatus(order.status().name());
        entity.setAssignedRobotId(order.assignedRobotId());
        dispatchOrderRepository.save(entity);
    }

    @Override
    public List<SpotItem> findSpotsBySku(String sku) {
        return spotItemRepository.findSpotsBySku(sku).stream()
                .map(row -> new SpotItem(
                        mapper.longValue(row[0]),
                        (String) row[1],
                        mapper.longValue(row[2]),
                        (String) row[3],
                        mapper.intValue(row[4])
                ))
                .toList();
    }

    @Override
    public List<RootPoint> findPickingRootPointsForOrder(Long orderId) {
        return rootPointRepository.findPickingRootPointsForOrder(orderId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RootPoint> findRootPoints() {
        return rootPointRepository.findAllByOrderByIdAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RouteEdge> findRouteEdges() {
        return routeEdgeRepository.findAllByOrderByIdAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public RoutePlan createRoutePlan(Long orderId, String robotId, BigDecimal totalDistance, BigDecimal speedFactor, List<RouteStep> steps) {
        RoutePlanJpaEntity routePlan = new RoutePlanJpaEntity();
        routePlan.setOrderId(orderId);
        routePlan.setRobotId(robotId);
        routePlan.setStatus(RoutePlanStatus.ACTIVE.name());
        routePlan.setTotalDistance(totalDistance);
        routePlan.setSpeedFactor(speedFactor);
        RoutePlanJpaEntity savedRoutePlan = routePlanRepository.saveAndFlush(routePlan);

        for (RouteStep step : steps) {
            RouteStepJpaEntity routeStep = new RouteStepJpaEntity();
            routeStep.setRoutePlanId(savedRoutePlan.getId());
            routeStep.setSequence(step.sequence());
            routeStep.setRootPointId(step.rootPointId());
            routeStep.setAction(step.action().name());
            routeStepRepository.save(routeStep);
        }
        return findRoutePlan(savedRoutePlan.getId()).orElseThrow();
    }

    @Override
    public Optional<RoutePlan> findRoutePlan(Long routePlanId) {
        return routePlanRepository.findById(routePlanId)
                .map(routePlan -> mapper.toDomain(routePlan, findRouteSteps(routePlanId)));
    }

    @Override
    public void saveRoutePlan(RoutePlan routePlan) {
        RoutePlanJpaEntity entity = routePlanRepository.findById(routePlan.id()).orElseThrow();
        entity.setStatus(routePlan.status().name());
        routePlanRepository.save(entity);
    }

    @Override
    public void reserveStockForOrder(Long orderId) {
        int requiredItems = orderItemRepository.findByOrderIdOrderByIdAsc(orderId).size();
        int availableItems = spotItemRepository.countAvailableItemsForOrder(orderId);
        if (requiredItems != availableItems) {
            throw new InsufficientStockException("Insufficient stock to assign robot");
        }
        spotItemRepository.reserveStockForOrder(orderId);
    }

    @Override
    public void decrementStockForOrder(Long orderId) {
        int requiredItems = orderItemRepository.findByOrderIdOrderByIdAsc(orderId).size();
        int reservedItems = spotItemRepository.countReservedItemsForOrder(orderId);
        if (reservedItems != requiredItems) {
            throw new InsufficientStockException("Insufficient reserved stock to complete route");
        }
        spotItemRepository.decrementStockForOrder(orderId);
    }

    @Override
    public List<RootPoint> findRootPointsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return rootPointRepository.findByIdInOrderByIdAsc(ids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private List<OrderItem> findDomainOrderItems(Long orderId) {
        return orderItemRepository.findDomainItemsByOrderId(orderId).stream()
                .map(row -> new OrderItem(
                        mapper.longValue(row[0]),
                        (String) row[1],
                        mapper.intValue(row[2]),
                        (Boolean) row[3]
                ))
                .toList();
    }

    private List<RouteStep> findRouteSteps(Long routePlanId) {
        List<RouteStepJpaEntity> steps = routeStepRepository.findByRoutePlanIdOrderBySequenceAsc(routePlanId);
        Map<Long, String> rootPointCodes = rootPointRepository.findByIdInOrderByIdAsc(
                        steps.stream().map(RouteStepJpaEntity::getRootPointId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(RootPointJpaEntity::getId, RootPointJpaEntity::getCode));
        return steps.stream()
                .map(step -> mapper.toDomain(step, rootPointCodes.get(step.getRootPointId())))
                .toList();
    }
}
