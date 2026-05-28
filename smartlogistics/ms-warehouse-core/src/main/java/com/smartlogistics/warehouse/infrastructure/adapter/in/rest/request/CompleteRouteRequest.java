package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request;

import jakarta.validation.constraints.Min;

public record CompleteRouteRequest(@Min(1) long durationSeconds) {
}
