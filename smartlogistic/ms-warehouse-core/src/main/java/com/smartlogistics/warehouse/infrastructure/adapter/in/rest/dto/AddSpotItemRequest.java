package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddSpotItemRequest(
    @NotNull Long itemId,
    @NotNull @Min(0) Integer quantityAvailable
) {}