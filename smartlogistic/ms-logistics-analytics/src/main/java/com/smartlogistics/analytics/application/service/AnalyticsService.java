package com.smartlogistics.analytics.application.service;

import com.smartlogistics.analytics.application.port.in.ProcessRouteEventUseCase;
import com.smartlogistics.analytics.application.port.out.AnalyticsRepositoryPort;
import com.smartlogistics.analytics.domain.model.RouteEvent;

public class AnalyticsService implements ProcessRouteEventUseCase {

    private final AnalyticsRepositoryPort repository;

    public AnalyticsService(AnalyticsRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void process(RouteEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("RouteEvent must not be null");
        }
        repository.save(event);
    }
}
