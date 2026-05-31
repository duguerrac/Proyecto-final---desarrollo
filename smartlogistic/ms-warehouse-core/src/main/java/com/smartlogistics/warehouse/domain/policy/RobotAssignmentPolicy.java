package com.smartlogistics.warehouse.domain.policy;

import com.smartlogistics.warehouse.domain.model.RobotStatus;

public class RobotAssignmentPolicy {
    public static final int MIN_BATTERY_LEVEL = 15;

    public boolean canAssign(RobotStatus status) {
        return status != null && status.available() && status.batteryLevel() >= MIN_BATTERY_LEVEL;
    }

    public String rejectionReason(RobotStatus status) {
        if (status == null) {
            return "Robot status unavailable";
        }
        if (!status.available()) {
            return "Robot is not available";
        }
        if (status.batteryLevel() < MIN_BATTERY_LEVEL) {
            return "Battery below 15%";
        }
        return "Robot accepted";
    }
}
