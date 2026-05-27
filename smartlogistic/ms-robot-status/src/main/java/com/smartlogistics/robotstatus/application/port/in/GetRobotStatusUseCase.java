package com.smartlogistics.robotstatus.application.port.in;

import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;
import java.util.Optional;

/**
 * Input port for querying robot status.
 * Pure Java — zero framework imports.
 */
public interface GetRobotStatusUseCase {

    Optional<Robot> getRobotStatus(String robotId);

    List<Robot> getAllRobots();

    List<Robot> getAvailableRobots();
}
