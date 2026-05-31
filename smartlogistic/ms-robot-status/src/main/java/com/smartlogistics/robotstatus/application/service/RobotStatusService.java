package com.smartlogistics.robotstatus.application.service;

import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.RegisterRobotUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.Robot;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RobotStatusService implements GetRobotStatusUseCase, RegisterRobotUseCase {

    private final RobotCachePort cache;

    public RobotStatusService(RobotCachePort cache) {
        this.cache = cache;
    }

    @Override
    public Robot getStatus(String robotId) {
        return cache.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));
    }

    @Override
    public List<Robot> getAllRobots() {
        return cache.findAll();
    }

    @Override
    public Robot register(Robot robot) {
        cache.save(robot);
        return robot;
    }
}
