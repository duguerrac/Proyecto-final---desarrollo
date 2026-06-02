package com.smartlogistics.robotstatus.application.port.in;

/**
 * Input port for robot dispatch operations.
 * Pure Java — zero framework imports.
 */
public interface DispatchRobotUseCase {

    /**
     * Find the ID of an available (IDLE, assignable) robot.
     *
     * @return robot ID or null if none available
     */
    String findAvailableRobot();

    /**
     * Mark a robot as available (IDLE) after completing its task.
     *
     * @param robotId the robot to mark available
     */
    void markRobotAvailable(String robotId);
}