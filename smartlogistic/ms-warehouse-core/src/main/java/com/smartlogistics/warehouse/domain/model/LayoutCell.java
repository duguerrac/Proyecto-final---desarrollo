package com.smartlogistics.warehouse.domain.model;

public record LayoutCell(
    Long id,
    Long layoutId,
    int rowIndex,
    int colIndex,
    CellType cellType
) {}