package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LayoutResponse(
    Long id,
    String name,
    int rows,
    int cols,
    BigDecimal cellSize,
    String status,
    LocalDateTime createdAt,
    LocalDateTime activatedAt,
    List<CellDTO> cells,
    List<SpotDTO> spots
) {}
