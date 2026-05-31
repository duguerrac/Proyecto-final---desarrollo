package com.smartlogistics.warehouse.infrastructure.adapter.out.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.warehouse.application.port.out.RouteEventPublisherPort;
import com.smartlogistics.warehouse.domain.model.RouteCompletedEvent;
import io.nats.client.Nats;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NatsRouteEventPublisher implements RouteEventPublisherPort {
    private final ObjectMapper objectMapper;
    private final String natsUrl;
    private final String subject;

    public NatsRouteEventPublisher(ObjectMapper objectMapper, @Value("${nats.url}") String natsUrl, @Value("${nats.subject:route.completed}") String subject) {
        this.objectMapper = objectMapper;
        this.natsUrl = natsUrl;
        this.subject = subject;
    }

    @Override
    public void publish(RouteCompletedEvent event) {
        try {
            publishPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize route event", ex);
        }
    }

    void publishPayload(String payload) {
        try (var connection = Nats.connect(natsUrl)) {
            connection.publish(subject, payload.getBytes(StandardCharsets.UTF_8));
            connection.flush(java.time.Duration.ofSeconds(2));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish route.completed to NATS", ex);
        }
    }
}
