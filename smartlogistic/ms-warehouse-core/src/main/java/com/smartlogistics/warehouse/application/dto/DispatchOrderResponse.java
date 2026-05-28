package com.smartlogistics.warehouse.application.dto;

import com.smartlogistics.warehouse.domain.model.DispatchOrder;
import java.time.Instant;
import java.util.List;

public record DispatchOrderResponse(Long id, String status, Instant createdAt, String priority, String assignedRobotId, List<ItemResponse> items) {
    public static DispatchOrderResponse from(DispatchOrder order) {
        return new DispatchOrderResponse(
                order.id(),
                order.status().name(),
                order.createdAt(),
                order.priority(),
                order.assignedRobotId(),
                order.items().stream().map(item -> new ItemResponse(item.sku(), item.quantity(), item.fragile())).toList()
        );
    }

    public record ItemResponse(String sku, int quantity, boolean fragile) {
    }
}
