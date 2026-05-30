package com.smartlogistics.robotstatus.application.service;

import com.smartlogistics.robotstatus.application.port.in.DispatchRobotUseCase;
import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.PublishSnapshotUseCase;
import com.smartlogistics.robotstatus.application.port.in.UpdateBatteryUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;

import java.util.List;
import java.util.Optional;

/**
 * Application service that orchestrates robot status use cases.
 * Pure Java — zero framework imports. Depends only on ports.
 */
public class RobotStatusService
        implements GetRobotStatusUseCase, UpdateBatteryUseCase,
        DispatchRobotUseCase, PublishSnapshotUseCase {

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

    @Override
    public void publishBatchSnapshot() {
        List<Robot> robots = robotCachePort.findAll();
        robotEventPort.publishStatusBatch(robots);
    }

    @Override
    public String findAvailableRobot() {
        return robotCachePort.findAll().stream()
                .filter(Robot::isAssignable)
                .findFirst()
                .map(robot -> {
                    robot.setAvailable(false);
                    robot.setOperationalMode(RobotStatus.MOVING);
                    robotCachePort.save(robot);
                    robotEventPort.publishStatusUpdate(robot);
                    return robot.getId();
                })
                .orElse(null);
    }

    @Override
    public void markRobotAvailable(String robotId) {
        robotCachePort.findById(robotId).ifPresent(robot -> {
            robot.setAvailable(true);
            robot.setOperationalMode(RobotStatus.IDLE);
            robotCachePort.save(robot);
            robotEventPort.publishStatusUpdate(robot);
        });
    }
}
