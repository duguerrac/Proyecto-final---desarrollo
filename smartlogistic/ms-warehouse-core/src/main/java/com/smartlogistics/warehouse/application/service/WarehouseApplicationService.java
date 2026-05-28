package com.smartlogistics.warehouse.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.warehouse.application.dto.*;
import com.smartlogistics.warehouse.application.port.in.*;
import com.smartlogistics.warehouse.application.port.out.*;
import com.smartlogistics.warehouse.domain.exception.BusinessException;
import com.smartlogistics.warehouse.domain.exception.NotFoundException;
import com.smartlogistics.warehouse.domain.exception.RobotRejectedException;
import com.smartlogistics.warehouse.domain.model.*;
import com.smartlogistics.warehouse.domain.policy.FragileProductPolicy;
import com.smartlogistics.warehouse.domain.policy.RobotAssignmentPolicy;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseApplicationService implements CreateDispatchOrderUseCase, GetDispatchOrderUseCase, AssignRobotUseCase,
        PlanRouteUseCase, CompleteRouteUseCase, GetInventorySpotsUseCase, GetWarehouseGraphUseCase {
    private final WarehouseRepositoryPort warehouseRepository;
    private final RobotStatusPort robotStatusPort;
    private final RouteEventPublisherPort eventPublisher;
    private final OutboxEventRepositoryPort outboxRepository;
    private final ObjectMapper objectMapper;
    private final RobotAssignmentPolicy robotAssignmentPolicy = new RobotAssignmentPolicy();
    private final FragileProductPolicy fragileProductPolicy = new FragileProductPolicy();
    private final DijkstraRouteCalculator routeCalculator = new DijkstraRouteCalculator();
    private final String warehouseId;
    private final String startRootPoint;
    private final String exitRootPoint;

    public WarehouseApplicationService(
            WarehouseRepositoryPort warehouseRepository,
            RobotStatusPort robotStatusPort,
            RouteEventPublisherPort eventPublisher,
            OutboxEventRepositoryPort outboxRepository,
            ObjectMapper objectMapper,
            @Value("${warehouse.id:WH-01}") String warehouseId,
            @Value("${warehouse.start-root-point:RP-START}") String startRootPoint,
            @Value("${warehouse.exit-root-point:RP-EXIT}") String exitRootPoint) {
        this.warehouseRepository = warehouseRepository;
        this.robotStatusPort = robotStatusPort;
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.warehouseId = warehouseId;
        this.startRootPoint = startRootPoint;
        this.exitRootPoint = exitRootPoint;
    }

    @Override
    @Transactional
    public DispatchOrderResponse create(CreateDispatchOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException("Order must contain at least one item");
        }
        List<OrderItem> items = command.items().stream().map(item -> {
            InventoryItem inventoryItem = warehouseRepository.findInventoryBySku(item.sku())
                    .orElseThrow(() -> new NotFoundException("SKU not found: " + item.sku()));
            return new OrderItem(inventoryItem.id(), inventoryItem.sku(), item.quantity(), inventoryItem.fragile());
        }).toList();
        DispatchOrder order = warehouseRepository.createOrder(defaultPriority(command.priority()), items);
        return DispatchOrderResponse.from(order);
    }

    @Override
    public DispatchOrderResponse get(Long orderId) {
        return DispatchOrderResponse.from(findOrder(orderId));
    }

    @Override
    @Transactional
    public AssignRobotResponse assign(AssignRobotCommand command) {
        DispatchOrder order = findOrder(command.orderId());
        RobotStatus robotStatus = robotStatusPort.getStatus(command.robotId());
        if (!robotAssignmentPolicy.canAssign(robotStatus)) {
            throw new RobotRejectedException(robotAssignmentPolicy.rejectionReason(robotStatus));
        }
        warehouseRepository.reserveStockForOrder(order.id());
        order.assignRobot(command.robotId());
        warehouseRepository.saveOrder(order);
        return new AssignRobotResponse(order.id(), command.robotId(), order.status().name(), "Robot assigned");
    }

    @Override
    @Transactional
    public RoutePlanResponse plan(PlanRouteCommand command) {
        DispatchOrder order = findOrder(command.orderId());
        if (order.assignedRobotId() == null || order.assignedRobotId().isBlank()) {
            throw new BusinessException("Order must have an assigned robot before planning route");
        }
        List<String> pickupCodes = warehouseRepository.findPickingRootPointsForOrder(order.id()).stream()
                .map(RootPoint::code)
                .toList();
        DijkstraRouteCalculator.RouteCalculation calculation = routeCalculator.calculate(
                warehouseRepository.findRootPoints(),
                warehouseRepository.findRouteEdges(),
                startRootPoint,
                pickupCodes,
                exitRootPoint
        );
        order.markRoutePlanned();
        warehouseRepository.saveOrder(order);
        RoutePlan routePlan = warehouseRepository.createRoutePlan(
                order.id(),
                order.assignedRobotId(),
                calculation.totalDistance(),
                fragileProductPolicy.speedFactor(order),
                calculation.steps()
        );
        return RoutePlanResponse.from(routePlan);
    }

    @Override
    @Transactional
    public RoutePlanResponse complete(CompleteRouteCommand command) {
        RoutePlan routePlan = warehouseRepository.findRoutePlan(command.routeId())
                .orElseThrow(() -> new NotFoundException("Route not found: " + command.routeId()));
        DispatchOrder order = findOrder(routePlan.orderId());
        routePlan.complete();
        order.complete();
        warehouseRepository.decrementStockForOrder(order.id());
        warehouseRepository.saveRoutePlan(routePlan);
        warehouseRepository.saveOrder(order);

        RouteCompletedEvent event = buildRouteCompletedEvent(routePlan, order, command.durationSeconds());
        Long outboxId = outboxRepository.save(String.valueOf(order.id()), RouteCompletedEvent.TYPE, serialize(event), OutboxEventStatus.PENDING);
        try {
            eventPublisher.publish(event);
            outboxRepository.markPublished(outboxId);
        } catch (RuntimeException ex) {
            outboxRepository.markFailed(outboxId);
        }
        return RoutePlanResponse.from(routePlan);
    }

    @Override
    public List<InventorySpotResponse> getBySku(String sku) {
        return warehouseRepository.findSpotsBySku(sku).stream()
                .map(spot -> new InventorySpotResponse(spot.sku(), spot.spotCode(), spot.quantityAvailable()))
                .toList();
    }

    @Override
    public WarehouseGraphResponse getGraph() {
        return new WarehouseGraphResponse(
                warehouseRepository.findRootPoints().stream()
                        .map(point -> new WarehouseGraphResponse.Node(point.id(), point.code(), point.x(), point.y(), point.zLevel(), point.type(), point.blocked()))
                        .toList(),
                warehouseRepository.findRouteEdges().stream()
                        .map(edge -> new WarehouseGraphResponse.Edge(edge.sourceId(), edge.targetId(), edge.distance(), edge.bidirectional(), edge.weight()))
                        .toList()
        );
    }

    private DispatchOrder findOrder(Long orderId) {
        return warehouseRepository.findOrder(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }

    private RouteCompletedEvent buildRouteCompletedEvent(RoutePlan routePlan, DispatchOrder order, long durationSeconds) {
        List<Long> ids = routePlan.steps().stream().map(RouteStep::rootPointId).distinct().toList();
        Map<Long, RootPoint> pointsById = warehouseRepository.findRootPointsByIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(RootPoint::id, Function.identity()));
        return new RouteCompletedEvent(
                UUID.randomUUID(),
                RouteCompletedEvent.TYPE,
                Instant.now(),
                order.id(),
                routePlan.robotId(),
                warehouseId,
                fragileProductPolicy.hasFragileItems(order),
                routePlan.totalDistance(),
                durationSeconds,
                routePlan.steps().stream()
                        .sorted(Comparator.comparingInt(RouteStep::sequence))
                        .map(step -> pointsById.get(step.rootPointId()))
                        .map(point -> new RouteCompletedEvent.PathPoint(point.code(), point.x(), point.y()))
                        .toList()
        );
    }

    private String serialize(RouteCompletedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Could not serialize route completed event");
        }
    }

    private String defaultPriority(String priority) {
        return priority == null || priority.isBlank() ? "NORMAL" : priority;
    }
}
