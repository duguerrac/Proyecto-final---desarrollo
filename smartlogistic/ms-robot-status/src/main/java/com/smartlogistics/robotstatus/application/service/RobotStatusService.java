package com.smartlogistics.robotstatus.application.service;

import com.smartlogistics.robotstatus.application.port.in.DispatchRobotUseCase;
import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.PublishSnapshotUseCase;
import com.smartlogistics.robotstatus.application.port.in.RegisterRobotUseCase;
import com.smartlogistics.robotstatus.application.port.in.UpdateBatteryUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application service that orchestrates robot status use cases.
 */
@Service
public class RobotStatusService
        implements GetRobotStatusUseCase, UpdateBatteryUseCase,
        DispatchRobotUseCase, PublishSnapshotUseCase, RegisterRobotUseCase {

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
    public Robot getStatus(String robotId) {
        return robotCachePort.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));
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
        // Only publish event if operationalMode or availability actually changed
        Optional<Robot> prev = robotCachePort.findById(robot.getId());
        boolean stateChanged = prev.isEmpty()
                || !prev.get().getOperationalMode().equals(robot.getOperationalMode())
                || prev.get().isAvailable() != robot.isAvailable();

        robotCachePort.save(robot);

        if (stateChanged) {
            robotEventPort.publishStatusUpdate(robot);
        }
        return robot;
    }

    @Override
    public Robot register(Robot robot) {
        robotCachePort.save(robot);
        return robot;
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
    public String findAvailableRobot(java.util.Map<String, Object> mission) {
        return robotCachePort.findAll().stream()
                .filter(Robot::isAssignable)
                .findFirst()
                .map(robot -> {
                    robot.setAvailable(false);
                    robot.setOperationalMode(RobotStatus.MOVING);
                    robot.setPendingMission(mission);
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
            robot.setPendingMission(null);
            robotCachePort.save(robot);
            robotEventPort.publishStatusUpdate(robot);
        });
    }
}