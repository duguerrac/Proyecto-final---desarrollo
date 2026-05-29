package com.smartlogistics.warehouse.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WarehouseLayout(
    Long id,
    String name,
    int rowsCount,
    int colsCount,
    BigDecimal cellSize,
    LayoutStatus status,
    LocalDateTime createdAt,
    LocalDateTime activatedAt,
    List<LayoutCell> cells
) {}