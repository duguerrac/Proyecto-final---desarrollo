package com.smartlogistics.warehouse.domain.policy;

import com.smartlogistics.warehouse.domain.model.DispatchOrder;
import java.math.BigDecimal;

public class FragileProductPolicy {
    private static final BigDecimal FRAGILE_SPEED_FACTOR = new BigDecimal("0.50");

    public boolean hasFragileItems(DispatchOrder order) {
        return order.hasFragileItems();
    }

    public BigDecimal speedFactor(DispatchOrder order) {
        return hasFragileItems(order) ? FRAGILE_SPEED_FACTOR : BigDecimal.ONE;
    }
}
