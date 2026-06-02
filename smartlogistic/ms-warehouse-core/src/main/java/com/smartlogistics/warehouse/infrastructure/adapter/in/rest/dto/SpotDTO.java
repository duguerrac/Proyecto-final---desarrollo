package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record SpotDTO(
    Long id,
    String code,
    String aisle,
    String section,
    BigDecimal x,
    BigDecimal y,
    String rootPointCode,
    List<SpotItemDTO> items
) {}