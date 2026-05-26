package com.smartlogistics.analytics.application.port.out;

import com.smartlogistics.analytics.domain.model.RouteEvent;

public interface AnalyticsRepositoryPort {
    void save(RouteEvent event);
}
