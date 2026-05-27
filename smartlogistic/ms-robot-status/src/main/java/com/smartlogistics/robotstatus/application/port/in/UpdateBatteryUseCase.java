package com.smartlogistics.robotstatus.application.port.in;

import com.smartlogistics.robotstatus.domain.model.Robot;

/**
 * Input port for updating robot state.
 * Pure Java — zero framework imports.
 */
public interface UpdateBatteryUseCase {

    Robot saveRobot(Robot robot);
}