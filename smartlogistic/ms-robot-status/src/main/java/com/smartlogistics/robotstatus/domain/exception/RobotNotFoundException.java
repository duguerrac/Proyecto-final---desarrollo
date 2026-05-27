package com.smartlogistics.robotstatus.domain.exception;

/**
 * Domain exception thrown when a robot cannot be found.
 * Pure Java — zero framework imports.
 */
public class RobotNotFoundException extends RuntimeException {

    public RobotNotFoundException(String robotId) {
        super("Robot not found: " + robotId);
    }
}