package com.smartlogistics.warehouse.infrastructure.adapter.out.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.warehouse.application.port.out.RouteEventPublisherPort;
import com.smartlogistics.warehouse.domain.model.RouteCompletedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitRouteEventPublisher implements RouteEventPublisherPort {

    static final String EXCHANGE = "logistics.exchange";
    static final String ROUTING_KEY = "route.completed";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitRouteEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
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
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, payload);
    }
}
