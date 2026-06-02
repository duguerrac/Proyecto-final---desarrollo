package com.smartlogistics.robotstatus.application.service;

import com.smartlogistics.robotstatus.application.port.in.SendRobotCommandUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotCommandPort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotCommand;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;

/**
 * Application service that orchestrates robot command use cases.
 * Pure Java — zero framework imports. Depends only on ports.
 */
public class RobotCommandService implements SendRobotCommandUseCase {

    private final RobotCachePort robotCachePort;
    private final RobotCommandPort robotCommandPort;
    private final RobotEventPort robotEventPort;

    public RobotCommandService(RobotCachePort robotCachePort,
                               RobotCommandPort robotCommandPort,
                               RobotEventPort robotEventPort) {
        this.robotCachePort = robotCachePort;
        this.robotCommandPort = robotCommandPort;
        this.robotEventPort = robotEventPort;
    }

    @Override
    public RobotCommand sendCommand(RobotCommand command) {
        // Validate command
        if (!command.isValid()) {
            throw new IllegalArgumentException("Invalid command: robotId and type are required");
        }

        // Verify robot exists
        Robot robot = robotCachePort.findById(command.getRobotId())
                .orElseThrow(() -> new RobotNotFoundException(command.getRobotId()));

        // Verify robot is assignable (for movement/pickup commands)
        if (!robot.isAssignable()) {
            throw new IllegalStateException(
                    "Robot " + command.getRobotId() + " is not available (battery="
                            + robot.getBatteryLevel() + "%, available=" + robot.isAvailable() + ")");
        }

        // Update robot status based on command type
        switch (command.getType()) {
            case GO_TO -> {
                robot.setOperationalMode(RobotStatus.MOVING);
                robot.setAvailable(false);
            }
            case PICK_UP -> {
                robot.setOperationalMode(RobotStatus.PICKING);
                robot.setAvailable(false);
            }
            case DROP_OFF -> {
                robot.setOperationalMode(RobotStatus.MOVING);
                robot.setAvailable(false);
            }
            case RETURN_DOCK -> {
                robot.setOperationalMode(RobotStatus.MOVING);
                robot.setAvailable(false);
            }
        }

        // Update target location if provided
        if (command.getTargetLocation() != null && !command.getTargetLocation().isBlank()) {
            robot.setCurrentLocation(command.getTargetLocation());
        }

        // Save updated robot status
        robotCachePort.save(robot);

        // Publish status update (robot state changed)
        robotEventPort.publishStatusUpdate(robot);

        // Publish command to robot via NATS
        robotCommandPort.publishCommand(command);

        return command;
    }
}