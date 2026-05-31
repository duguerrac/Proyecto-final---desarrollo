package com.smartlogistics.warehouse.application.port.in;

import com.smartlogistics.warehouse.application.dto.CreateDispatchOrderCommand;
import com.smartlogistics.warehouse.application.dto.DispatchOrderResponse;

public interface CreateDispatchOrderUseCase {
    DispatchOrderResponse create(CreateDispatchOrderCommand command);
}
