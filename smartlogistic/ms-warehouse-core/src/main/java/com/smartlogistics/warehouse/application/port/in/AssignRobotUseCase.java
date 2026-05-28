package com.smartlogistics.warehouse.application.port.in;

import com.smartlogistics.warehouse.application.dto.AssignRobotCommand;
import com.smartlogistics.warehouse.application.dto.AssignRobotResponse;

public interface AssignRobotUseCase {
    AssignRobotResponse assign(AssignRobotCommand command);
}
