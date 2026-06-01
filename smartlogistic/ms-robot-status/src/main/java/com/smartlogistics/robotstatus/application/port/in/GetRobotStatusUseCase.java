package com.smartlogistics.robotstatus.application.port.in;

import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;
import java.util.Optional;

public interface GetRobotStatusUseCase {
    Optional<Robot> getRobotStatus(String robotId);
    Robot getStatus(String robotId);
    List<Robot> getAllRobots();
    List<Robot> getAvailableRobots();
    Robot saveRobot(Robot robot);
}
