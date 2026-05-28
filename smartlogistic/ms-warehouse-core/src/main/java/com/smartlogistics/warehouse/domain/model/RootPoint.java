package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;

public record RootPoint(Long id, String code, BigDecimal x, BigDecimal y, int zLevel, String type, boolean blocked) {
}
