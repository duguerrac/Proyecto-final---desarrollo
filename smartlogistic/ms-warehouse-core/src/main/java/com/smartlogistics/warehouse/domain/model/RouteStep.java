package com.smartlogistics.warehouse.domain.model;

public record RouteStep(int sequence, Long rootPointId, String rootPointCode, RouteStepAction action) {
}
