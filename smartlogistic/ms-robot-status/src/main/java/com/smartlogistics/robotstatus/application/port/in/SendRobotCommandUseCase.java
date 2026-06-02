package com.smartlogistics.robotstatus.application.port.in;

import com.smartlogistics.robotstatus.domain.model.RobotCommand;

/**
 * Input port for sending commands to robots.
 * Pure Java — zero framework imports.
 */
public interface SendRobotCommandUseCase {

    /**
     * Send a command to a robot.
     * Validates the robot exists and is available, updates its status,
     * and publishes the command via NATS.
     *
     * @param command the command to send
     * @return the updated command with confirmation
     * @throws com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException if robot doesn't exist
     * @throws IllegalStateException if robot is not assignable
     */
    RobotCommand sendCommand(RobotCommand command);
}