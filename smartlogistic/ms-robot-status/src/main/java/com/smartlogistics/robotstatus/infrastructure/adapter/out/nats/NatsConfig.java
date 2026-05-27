package com.smartlogistics.robotstatus.infrastructure.adapter.out.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * NATS connection configuration.
 * Infrastructure layer — framework imports allowed.
 */
@Configuration
public class NatsConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Bean
    public Connection natsConnection() throws Exception {
        log.info("Connecting to NATS at {}", natsUrl);
        Options options = Options.builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(5))
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1) // reconnect forever
                .connectionName("ms-robot-status")
                .build();
        Connection connection = Nats.connect(options);
        log.info("Connected to NATS server: {}", connection.getServerInfo());
        return connection;
    }
}