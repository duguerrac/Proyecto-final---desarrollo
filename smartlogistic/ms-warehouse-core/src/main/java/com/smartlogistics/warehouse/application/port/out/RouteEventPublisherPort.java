package com.smartlogistics.warehouse.application.port.out;

import com.smartlogistics.warehouse.domain.model.RouteCompletedEvent;

public interface RouteEventPublisherPort {
    void publish(RouteCompletedEvent event);
}
