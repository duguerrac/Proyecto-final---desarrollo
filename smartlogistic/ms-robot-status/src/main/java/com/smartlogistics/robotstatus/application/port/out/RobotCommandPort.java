package com.smartlogistics.robotstatus.application.port.out;

import com.smartlogistics.robotstatus.domain.model.RobotCommand;

/**
 * Output port for publishing robot commands (via NATS).
 * Pure Java — zero framework imports.
 */
public interface RobotCommandPort {

    /**
     * Publish a command to a robot via messaging.
     *
     * @param command the command to publish
     */
    void publishCommand(RobotCommand command);
}