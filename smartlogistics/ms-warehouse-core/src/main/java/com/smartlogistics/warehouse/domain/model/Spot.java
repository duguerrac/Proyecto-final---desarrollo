package com.smartlogistics.warehouse.domain.model;

public record Spot(Long id, String code, String aisle, String section, int level, Long rootPointId) {
}
