package com.smartlogistics.warehouse.application.dto;

public record AssignRobotResponse(Long orderId, String robotId, String status, String message) {
}
