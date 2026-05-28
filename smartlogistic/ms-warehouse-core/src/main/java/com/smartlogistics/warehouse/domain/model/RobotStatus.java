package com.smartlogistics.warehouse.domain.model;

public record RobotStatus(String robotId, int batteryLevel, boolean available, String currentLocation, String operationalMode) {
}
