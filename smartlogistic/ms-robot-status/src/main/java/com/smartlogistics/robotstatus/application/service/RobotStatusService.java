package com.smartlogistics.robotstatus.application.service;

import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.UpdateBatteryUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;
import java.util.Optional;

/**
 * Application service that orchestrates robot status use cases.
 * Pure Java — zero framework imports. Depends only on ports.
 */
public class RobotStatusService implements GetRobotStatusUseCase, UpdateBatteryUseCase {

    private final RobotCachePort robotCachePort;
    private final RobotEventPort robotEventPort;

    public RobotStatusService(RobotCachePort robotCachePort, RobotEventPort robotEventPort) {
        this.robotCachePort = robotCachePort;
        this.robotEventPort = robotEventPort;
    }

    @Override
    public Optional<Robot> getRobotStatus(String robotId) {
        return robotCachePort.findById(robotId);
    }

    @Override
    public List<Robot> getAllRobots() {
        return robotCachePort.findAll();
    }

    @Override
    public List<Robot> getAvailableRobots() {
        return robotCachePort.findAll().stream()
                .filter(Robot::isAssignable)
                .toList();
    }

    @Override
    public Robot saveRobot(Robot robot) {
        Robot saved = robotCachePort.save(robot);
        robotEventPort.publishStatusUpdate(saved);
        return saved;
    }

    /**
     * Publish a snapshot of all robots to NATS.
     * Called by the infrastructure layer (e.g., scheduled task or after seed).
     */
    public void publishBatchSnapshot() {
        List<Robot> robots = robotCachePort.findAll();
        robotEventPort.publishStatusBatch(robots);
    }
}
