package com.smartlogistics.robotstatus.application.port.in;

import com.smartlogistics.robotstatus.domain.model.Robot;

public interface RegisterRobotUseCase {
    Robot register(Robot robot);
}
