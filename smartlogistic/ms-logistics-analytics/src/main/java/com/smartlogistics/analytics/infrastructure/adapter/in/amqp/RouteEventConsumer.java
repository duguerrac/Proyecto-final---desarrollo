package com.smartlogistics.analytics.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.analytics.application.port.in.ProcessRouteEventUseCase;
import com.smartlogistics.analytics.domain.exception.EventProcessingException;
import com.smartlogistics.analytics.domain.model.RouteEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RouteEventConsumer {

    private final ProcessRouteEventUseCase processRouteEventUseCase;
    private final ObjectMapper objectMapper;

    public RouteEventConsumer(ProcessRouteEventUseCase processRouteEventUseCase, ObjectMapper objectMapper) {
        this.processRouteEventUseCase = processRouteEventUseCase;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "route.completed.q")
    public void onRouteCompleted(String payload) {
        try {
            RouteEventMessage message = objectMapper.readValue(payload, RouteEventMessage.class);
            RouteEvent event = new RouteEvent(
                    message.eventId(),
                    message.orderId(),
                    message.robotId(),
                    message.path() != null ? message.path() : List.of(),
                    message.distance(),
                    message.duration(),
                    message.timestamp() != null ? Instant.parse(message.timestamp()) : Instant.now()
            );
            processRouteEventUseCase.process(event);
        } catch (JsonProcessingException e) {
            throw new EventProcessingException("Failed to deserialize route event: " + payload, e);
        }
    }

    public record RouteEventMessage(
            String eventId,
            String orderId,
            String robotId,
            List<String> path,
            double distance,
            long duration,
            String timestamp
    ) {}
}
