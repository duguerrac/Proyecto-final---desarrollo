package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateLayoutRequest(
    String name,
    int rows,
    int cols,
    BigDecimal cellSize,
    List<CellDTO> cells
) {}