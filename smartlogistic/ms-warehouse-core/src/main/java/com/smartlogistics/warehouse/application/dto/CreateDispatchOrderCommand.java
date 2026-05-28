package com.smartlogistics.warehouse.application.dto;

import java.util.List;

public record CreateDispatchOrderCommand(List<ItemCommand> items, String priority) {
    public record ItemCommand(String sku, int quantity) {
    }
}
