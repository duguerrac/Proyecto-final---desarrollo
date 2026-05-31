package com.smartlogistics.analytics.application.port.in;

import com.smartlogistics.analytics.domain.model.RouteEvent;

public interface ProcessRouteEventUseCase {
    void process(RouteEvent event);
}
