package com.smartlogistics.analytics.infrastructure.config;

import com.smartlogistics.analytics.application.port.in.ProcessRouteEventUseCase;
import com.smartlogistics.analytics.application.port.out.AnalyticsRepositoryPort;
import com.smartlogistics.analytics.application.service.AnalyticsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public ProcessRouteEventUseCase processRouteEventUseCase(AnalyticsRepositoryPort repository) {
        return new AnalyticsService(repository);
    }
}
