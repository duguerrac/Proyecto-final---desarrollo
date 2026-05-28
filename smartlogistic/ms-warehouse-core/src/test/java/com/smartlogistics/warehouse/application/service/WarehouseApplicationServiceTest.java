package com.smartlogistics.warehouse.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.warehouse.application.dto.AssignRobotCommand;
import com.smartlogistics.warehouse.application.dto.CompleteRouteCommand;
import com.smartlogistics.warehouse.application.dto.PlanRouteCommand;
import com.smartlogistics.warehouse.application.port.out.OutboxEventRepositoryPort;
import com.smartlogistics.warehouse.application.port.out.RobotStatusPort;
import com.smartlogistics.warehouse.application.port.out.RouteEventPublisherPort;
import com.smartlogistics.warehouse.application.port.out.WarehouseRepositoryPort;
import com.smartlogistics.warehouse.domain.exception.RobotRejectedException;
import com.smartlogistics.warehouse.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WarehouseApplicationServiceTest {
    private final WarehouseRepositoryPort warehouseRepository = mock(WarehouseRepositoryPort.class);
    private final RobotStatusPort robotStatusPort = mock(RobotStatusPort.class);
    private final RouteEventPublisherPort eventPublisher = mock(RouteEventPublisherPort.class);
    private final OutboxEventRepositoryPort outboxRepository = mock(OutboxEventRepositoryPort.class);
    private final WarehouseApplicationService service = new WarehouseApplicationService(
            warehouseRepository,
            robotStatusPort,
            eventPublisher,
            outboxRepository,
            new ObjectMapper().findAndRegisterModules(),
            "WH-01",
            "RP-START",
            "RP-EXIT"
    );

    @Test
    void reservesStockBeforeAssigningRobot() {
        DispatchOrder order = pendingOrder();
        when(warehouseRepository.findOrder(1L)).thenReturn(Optional.of(order));
        when(robotStatusPort.getStatus("RBT-01")).thenReturn(new RobotStatus("RBT-01", 80, true, "RP-START", "AUTO"));

        var response = service.assign(new AssignRobotCommand(1L, "RBT-01"));

        assertThat(response.status()).isEqualTo(DispatchOrderStatus.ASSIGNED.name());
        var inOrder = inOrder(warehouseRepository);
        inOrder.verify(warehouseRepository).findOrder(1L);
        inOrder.verify(warehouseRepository).reserveStockForOrder(1L);
        inOrder.verify(warehouseRepository).saveOrder(order);
    }

    @Test
    void doesNotReserveStockWhenRobotIsRejected() {
        when(warehouseRepository.findOrder(1L)).thenReturn(Optional.of(pendingOrder()));
        when(robotStatusPort.getStatus("RBT-LOW")).thenReturn(new RobotStatus("RBT-LOW", 10, true, "RP-START", "AUTO"));

        assertThatThrownBy(() -> service.assign(new AssignRobotCommand(1L, "RBT-LOW")))
                .isInstanceOf(RobotRejectedException.class);

        verify(warehouseRepository, never()).reserveStockForOrder(anyLong());
        verify(warehouseRepository, never()).saveOrder(any());
    }

    @Test
    void appliesFragileSpeedFactorWhenPlanningRoute() {
        DispatchOrder order = assignedOrderWithFragileItem();
        when(warehouseRepository.findOrder(1L)).thenReturn(Optional.of(order));
        when(warehouseRepository.findPickingRootPointsForOrder(1L)).thenReturn(List.of(rootPoint(2L, "RP-A1")));
        when(warehouseRepository.findRootPoints()).thenReturn(List.of(
                rootPoint(1L, "RP-START"),
                rootPoint(2L, "RP-A1"),
                rootPoint(3L, "RP-EXIT")
        ));
        when(warehouseRepository.findRouteEdges()).thenReturn(List.of(
                new RouteEdge(1L, 2L, new BigDecimal("10.00"), true, BigDecimal.ONE),
                new RouteEdge(2L, 3L, new BigDecimal("5.00"), true, BigDecimal.ONE)
        ));
        when(warehouseRepository.createRoutePlan(eq(1L), eq("RBT-01"), any(), eq(new BigDecimal("0.50")), any()))
                .thenAnswer(invocation -> new RoutePlan(
                        10L,
                        1L,
                        "RBT-01",
                        RoutePlanStatus.ACTIVE,
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        Instant.now(),
                        invocation.getArgument(4)
                ));

        var response = service.plan(new PlanRouteCommand(1L));

        assertThat(response.speedFactor()).isEqualByComparingTo("0.50");
        assertThat(response.totalDistance()).isEqualByComparingTo("2");
        verify(warehouseRepository).createRoutePlan(eq(1L), eq("RBT-01"), eq(new BigDecimal("2")), eq(new BigDecimal("0.50")), any());
    }

    @Test
    void publishesRouteCompletedEventWithOrderedContractPath() {
        RoutePlan routePlan = completedRoutePlanFixture(RoutePlanStatus.ACTIVE);
        when(warehouseRepository.findRoutePlan(10L)).thenReturn(Optional.of(routePlan));
        when(warehouseRepository.findOrder(1L)).thenReturn(Optional.of(assignedOrderWithFragileItem()));
        when(warehouseRepository.findRootPointsByIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                rootPoint(3L, "RP-EXIT"),
                rootPoint(1L, "RP-START"),
                rootPoint(2L, "RP-A1")
        ));
        when(outboxRepository.save(eq("1"), eq(RouteCompletedEvent.TYPE), anyString(), eq(OutboxEventStatus.PENDING))).thenReturn(100L);

        service.complete(new CompleteRouteCommand(10L, 45));

        ArgumentCaptor<RouteCompletedEvent> eventCaptor = ArgumentCaptor.forClass(RouteCompletedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        RouteCompletedEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("route.completed");
        assertThat(event.path()).extracting(RouteCompletedEvent.PathPoint::rootPointId)
                .containsExactly("RP-START", "RP-A1", "RP-EXIT");
        verify(outboxRepository).markPublished(100L);
        verify(outboxRepository, never()).markFailed(anyLong());
    }

    @Test
    void keepsOutboxPendingWhenRouteEventPublishFails() {
        RoutePlan routePlan = completedRoutePlanFixture(RoutePlanStatus.ACTIVE);
        when(warehouseRepository.findRoutePlan(10L)).thenReturn(Optional.of(routePlan));
        when(warehouseRepository.findOrder(1L)).thenReturn(Optional.of(assignedOrderWithFragileItem()));
        when(warehouseRepository.findRootPointsByIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                rootPoint(1L, "RP-START"),
                rootPoint(2L, "RP-A1"),
                rootPoint(3L, "RP-EXIT")
        ));
        when(outboxRepository.save(eq("1"), eq(RouteCompletedEvent.TYPE), anyString(), eq(OutboxEventStatus.PENDING))).thenReturn(100L);
        doThrow(new RuntimeException("nats down")).when(eventPublisher).publish(any());

        service.complete(new CompleteRouteCommand(10L, 45));

        verify(outboxRepository).markFailed(100L);
        verify(outboxRepository, never()).markPublished(anyLong());
    }

    private DispatchOrder pendingOrder() {
        return new DispatchOrder(1L, Instant.now(), "NORMAL", DispatchOrderStatus.PENDING, null,
                List.of(new OrderItem(1L, "SKU-ELEC-001", 2, false)));
    }

    private DispatchOrder assignedOrderWithFragileItem() {
        return new DispatchOrder(1L, Instant.now(), "NORMAL", DispatchOrderStatus.ASSIGNED, "RBT-01",
                List.of(new OrderItem(1L, "SKU-FRAG-001", 1, true)));
    }

    private RootPoint rootPoint(Long id, String code) {
        return new RootPoint(id, code, BigDecimal.ZERO, BigDecimal.ZERO, 1, "INTERNAL", false);
    }

    private RoutePlan completedRoutePlanFixture(RoutePlanStatus status) {
        return new RoutePlan(
                10L,
                1L,
                "RBT-01",
                status,
                new BigDecimal("3"),
                new BigDecimal("0.50"),
                Instant.now(),
                List.of(
                        new RouteStep(1, 1L, "RP-START", RouteStepAction.NAVIGATE),
                        new RouteStep(2, 2L, "RP-A1", RouteStepAction.PICKUP),
                        new RouteStep(3, 3L, "RP-EXIT", RouteStepAction.EXIT)
                )
        );
    }
}
