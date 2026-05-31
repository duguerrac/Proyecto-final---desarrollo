package com.smartlogistics.analytics.domain.model;

import java.math.BigDecimal;

public record PathPoint(String rootPointId, BigDecimal x, BigDecimal y) {
}
