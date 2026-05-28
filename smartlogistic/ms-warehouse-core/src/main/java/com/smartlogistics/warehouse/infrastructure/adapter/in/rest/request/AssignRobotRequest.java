package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record AssignRobotRequest(@NotBlank String robotId) {
}
