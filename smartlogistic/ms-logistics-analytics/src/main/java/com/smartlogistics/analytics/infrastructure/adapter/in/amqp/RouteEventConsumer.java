package com.smartlogistics.analytics.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.analytics.application.port.in.ProcessRouteEventUseCase;
import com.smartlogistics.analytics.domain.exception.EventProcessingException;
import com.smartlogistics.analytics.domain.model.PathPoint;
import com.smartlogistics.analytics.domain.model.RouteEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
                    message.eventId().toString(),
                    message.eventType(),
                    message.orderId().toString(),
                    message.robotId(),
                    message.warehouseId(),
                    message.fragileItems(),
                    message.path() != null ? message.path() : List.of(),
                    message.totalDistanceMeters().doubleValue(),
                    message.durationSeconds(),
                    message.occurredAt() != null ? message.occurredAt() : Instant.now()
            );
            processRouteEventUseCase.process(event);
        } catch (JsonProcessingException e) {
            throw new EventProcessingException("Failed to deserialize route event: " + payload, e);
        }
    }

    public record RouteEventMessage(
            UUID eventId,
            @JsonProperty("eventType") String eventType,
            Long orderId,
            String robotId,
            String warehouseId,
            boolean fragileItems,
            List<PathPoint> path,
            BigDecimal totalDistanceMeters,
            long durationSeconds,
            Instant occurredAt
    ) {}
}
