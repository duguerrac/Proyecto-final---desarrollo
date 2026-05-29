package com.smartlogistics.warehouse.infrastructure.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NatsConfig {

    @Bean
    Connection natsConnection(@Value("${nats.url}") String natsUrl) throws Exception {
        return Nats.connect(natsUrl);
    }
}