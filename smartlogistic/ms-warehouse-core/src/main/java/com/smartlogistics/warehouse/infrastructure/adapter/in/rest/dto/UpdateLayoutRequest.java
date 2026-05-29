package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.util.List;

public record UpdateLayoutRequest(
    String name,
    List<CellDTO> cells
) {}