package com.smartlogistics.warehouse.application.port.out;

import com.smartlogistics.warehouse.domain.model.RobotStatus;

public interface RobotStatusPort {
    RobotStatus getStatus(String robotId);
}
