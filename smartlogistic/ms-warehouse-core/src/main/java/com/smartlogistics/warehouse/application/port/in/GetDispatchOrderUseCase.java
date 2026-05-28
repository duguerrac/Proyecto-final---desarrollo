package com.smartlogistics.warehouse.application.port.in;

import com.smartlogistics.warehouse.application.dto.DispatchOrderResponse;

public interface GetDispatchOrderUseCase {
    DispatchOrderResponse get(Long orderId);
}
