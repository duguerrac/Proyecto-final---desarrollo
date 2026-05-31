package com.smartlogistics.robotstatus.application.port.in;

import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;

public interface GetRobotStatusUseCase {
    Robot getStatus(String robotId);
    List<Robot> getAllRobots();
}
