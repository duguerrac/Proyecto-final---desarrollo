package com.smartlogistics.warehouse.application.dto;

public record InventorySpotResponse(String sku, String spotCode, int quantityAvailable) {
}
