package com.smartlogistics.warehouse.domain.model;

public record OrderItem(Long itemId, String sku, int quantity, boolean fragile) {
    public OrderItem {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }
}
