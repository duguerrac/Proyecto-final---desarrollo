package com.smartlogistics.warehouse.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record WarehouseGraphResponse(List<Node> nodes, List<Edge> edges) {
    public record Node(Long id, String code, BigDecimal x, BigDecimal y, int zLevel, String type, boolean blocked) {
    }

    public record Edge(Long sourceId, Long targetId, BigDecimal distance, boolean bidirectional, BigDecimal weight) {
    }
}
