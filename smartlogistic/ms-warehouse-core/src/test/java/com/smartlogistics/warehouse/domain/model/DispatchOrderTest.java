package com.smartlogistics.warehouse.domain.model;

import com.smartlogistics.warehouse.domain.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchOrderTest {
    @Test
    void assignsRobotToPendingOrder() {
        DispatchOrder order = new DispatchOrder(1L, Instant.now(), "NORMAL", DispatchOrderStatus.PENDING, null,
                List.of(new OrderItem(1L, "SKU-001", 2, false)));

        order.assignRobot("RBT-01");

        assertThat(order.status()).isEqualTo(DispatchOrderStatus.ASSIGNED);
        assertThat(order.assignedRobotId()).isEqualTo("RBT-01");
    }

    @Test
    void rejectsAssigningRobotWhenOrderIsNotPending() {
        DispatchOrder order = new DispatchOrder(1L, Instant.now(), "NORMAL", DispatchOrderStatus.COMPLETED, null,
                List.of(new OrderItem(1L, "SKU-001", 2, false)));

        assertThatThrownBy(() -> order.assignRobot("RBT-01"))
                .isInstanceOf(BusinessException.class);
    }
}
