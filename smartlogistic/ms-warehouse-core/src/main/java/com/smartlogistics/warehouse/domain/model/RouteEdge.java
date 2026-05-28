package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;

public record RouteEdge(Long sourceId, Long targetId, BigDecimal distance, boolean bidirectional, BigDecimal weight) {
}
