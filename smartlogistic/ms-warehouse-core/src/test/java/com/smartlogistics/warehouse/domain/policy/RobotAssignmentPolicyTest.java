package com.smartlogistics.warehouse.domain.policy;

import com.smartlogistics.warehouse.domain.model.RobotStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotAssignmentPolicyTest {
    private final RobotAssignmentPolicy policy = new RobotAssignmentPolicy();

    @Test
    void acceptsAvailableRobotWithEnoughBattery() {
        assertThat(policy.canAssign(new RobotStatus("RBT-01", 80, true, "RP-START", "AUTO"))).isTrue();
    }

    @Test
    void rejectsRobotWithLowBattery() {
        RobotStatus status = new RobotStatus("RBT-LOW", 10, true, "RP-START", "AUTO");

        assertThat(policy.canAssign(status)).isFalse();
        assertThat(policy.rejectionReason(status)).isEqualTo("Battery below 15%");
    }

    @Test
    void rejectsUnavailableRobot() {
        RobotStatus status = new RobotStatus("RBT-02", 80, false, "RP-START", "MAINTENANCE");

        assertThat(policy.canAssign(status)).isFalse();
    }
}
