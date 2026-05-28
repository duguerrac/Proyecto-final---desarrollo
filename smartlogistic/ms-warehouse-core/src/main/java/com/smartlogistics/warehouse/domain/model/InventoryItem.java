package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;

public record InventoryItem(Long id, String sku, String name, boolean fragile, BigDecimal defaultSpeedLimit) {
}
