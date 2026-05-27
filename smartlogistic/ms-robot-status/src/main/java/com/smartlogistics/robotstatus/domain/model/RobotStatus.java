package com.smartlogistics.robotstatus.domain.model;

/**
 * Value Object representing the operational status of a robot.
 * Pure Java — zero framework imports.
 */
public enum RobotStatus {
    IDLE,
    MOVING,
    PICKING,
    CHARGING,
    OFFLINE
}