package com.smartlogistics.robotstatus.domain.model;

/**
 * Operational modes for a warehouse robot.
 */
public enum RobotStatus {
    IDLE,
    MOVING,
    PICKING,
    LOADING,
    UNLOADING,
    CHARGING,
    ERROR,
    DISPATCHED
}