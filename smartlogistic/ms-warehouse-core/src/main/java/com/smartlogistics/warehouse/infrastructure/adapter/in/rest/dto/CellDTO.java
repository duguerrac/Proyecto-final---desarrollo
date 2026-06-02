package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

public record CellDTO(
    int rowIndex,
    int colIndex,
    String cellType
) {}