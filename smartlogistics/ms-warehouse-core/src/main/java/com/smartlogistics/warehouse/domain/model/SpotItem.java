package com.smartlogistics.warehouse.domain.model;

public record SpotItem(Long spotId, String spotCode, Long itemId, String sku, int quantityAvailable) {
}
