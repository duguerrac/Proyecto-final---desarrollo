package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.application.dto.*;
import com.smartlogistics.warehouse.application.port.in.*;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request.AssignRobotRequest;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request.CreateDispatchOrderRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final CreateDispatchOrderUseCase createDispatchOrder;
    private final GetDispatchOrderUseCase getDispatchOrder;
    private final AssignRobotUseCase assignRobot;
    private final PlanRouteUseCase planRoute;

    public OrderController(CreateDispatchOrderUseCase createDispatchOrder, GetDispatchOrderUseCase getDispatchOrder,
                           AssignRobotUseCase assignRobot, PlanRouteUseCase planRoute) {
        this.createDispatchOrder = createDispatchOrder;
        this.getDispatchOrder = getDispatchOrder;
        this.assignRobot = assignRobot;
        this.planRoute = planRoute;
    }

    @PostMapping
    public ResponseEntity<DispatchOrderResponse> create(@Valid @RequestBody CreateDispatchOrderRequest request) {
        CreateDispatchOrderCommand command = new CreateDispatchOrderCommand(
                request.items().stream().map(item -> new CreateDispatchOrderCommand.ItemCommand(item.sku(), item.quantity())).toList(),
                request.priority()
        );
        DispatchOrderResponse response = createDispatchOrder.create(command);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public DispatchOrderResponse get(@PathVariable Long id) {
        return getDispatchOrder.get(id);
    }

    @PostMapping("/{id}/assign-robot")
    public AssignRobotResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRobotRequest request) {
        return assignRobot.assign(new AssignRobotCommand(id, request.robotId()));
    }

    @PostMapping("/{id}/route-plan")
    public RoutePlanResponse plan(@PathVariable Long id) {
        return planRoute.plan(new PlanRouteCommand(id));
    }
}
