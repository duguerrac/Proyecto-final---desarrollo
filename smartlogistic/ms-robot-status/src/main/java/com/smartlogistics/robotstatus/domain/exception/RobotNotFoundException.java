package com.smartlogistics.robotstatus.domain.exception;

public class RobotNotFoundException extends RuntimeException {

    public RobotNotFoundException(String robotId) {
        super("Robot not found: " + robotId);
    }
}
