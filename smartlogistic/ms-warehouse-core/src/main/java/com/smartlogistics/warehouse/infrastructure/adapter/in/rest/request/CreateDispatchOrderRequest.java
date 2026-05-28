package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateDispatchOrderRequest(@NotEmpty List<@Valid ItemRequest> items, String priority) {
    public record ItemRequest(@NotBlank String sku, @Min(1) int quantity) {
    }
}
