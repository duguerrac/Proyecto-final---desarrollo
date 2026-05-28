package com.smartlogistics.warehouse.domain.model;

import com.smartlogistics.warehouse.domain.exception.BusinessException;
import java.time.Instant;
import java.util.List;

public class DispatchOrder {
    private final Long id;
    private final Instant createdAt;
    private final String priority;
    private final List<OrderItem> items;
    private DispatchOrderStatus status;
    private String assignedRobotId;

    public DispatchOrder(Long id, Instant createdAt, String priority, DispatchOrderStatus status, String assignedRobotId, List<OrderItem> items) {
        this.id = id;
        this.createdAt = createdAt;
        this.priority = priority;
        this.status = status;
        this.assignedRobotId = assignedRobotId;
        this.items = List.copyOf(items);
    }

    public void assignRobot(String robotId) {
        if (status != DispatchOrderStatus.PENDING) {
            throw new BusinessException("Only pending orders can assign a robot");
        }
        this.assignedRobotId = robotId;
        this.status = DispatchOrderStatus.ASSIGNED;
    }

    public void markRoutePlanned() {
        if (status != DispatchOrderStatus.ASSIGNED) {
            throw new BusinessException("Order must have an assigned robot before planning a route");
        }
        this.status = DispatchOrderStatus.ROUTE_PLANNED;
    }

    public void complete() {
        if (status == DispatchOrderStatus.COMPLETED) {
            throw new BusinessException("Order is already completed");
        }
        this.status = DispatchOrderStatus.COMPLETED;
    }

    public boolean hasFragileItems() {
        return items.stream().anyMatch(OrderItem::fragile);
    }

    public Long id() { return id; }
    public Instant createdAt() { return createdAt; }
    public String priority() { return priority; }
    public DispatchOrderStatus status() { return status; }
    public String assignedRobotId() { return assignedRobotId; }
    public List<OrderItem> items() { return items; }
}
