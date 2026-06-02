package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

public record SpotItemDTO(
    Long productId,
    String productName,
    String sku,
    int quantity
) {}