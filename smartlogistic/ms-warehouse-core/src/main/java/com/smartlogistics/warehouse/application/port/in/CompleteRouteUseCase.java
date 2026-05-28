package com.smartlogistics.warehouse.application.port.in;

import com.smartlogistics.warehouse.application.dto.CompleteRouteCommand;
import com.smartlogistics.warehouse.application.dto.RoutePlanResponse;

public interface CompleteRouteUseCase {
    RoutePlanResponse complete(CompleteRouteCommand command);
}
