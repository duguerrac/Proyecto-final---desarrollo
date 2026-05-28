package com.smartlogistics.warehouse.application.port.in;

import com.smartlogistics.warehouse.application.dto.PlanRouteCommand;
import com.smartlogistics.warehouse.application.dto.RoutePlanResponse;

public interface PlanRouteUseCase {
    RoutePlanResponse plan(PlanRouteCommand command);
}
