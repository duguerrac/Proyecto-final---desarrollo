package com.smartlogistics.robotstatus.application.port.out;

import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;
import java.util.Optional;

/**
 * Output port for robot persistence (Redis cache).
 * Pure Java — zero framework imports.
 */
public interface RobotCachePort {

    Optional<Robot> findById(String robotId);

    List<Robot> findAll();

    Robot save(Robot robot);
}
