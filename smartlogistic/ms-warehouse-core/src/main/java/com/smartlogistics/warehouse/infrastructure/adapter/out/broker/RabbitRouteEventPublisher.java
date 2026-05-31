package com.smartlogistics.warehouse.infrastructure.adapter.out.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.warehouse.application.port.out.RouteEventPublisherPort;
import com.smartlogistics.warehouse.domain.model.RouteCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitRouteEventPublisher implements RouteEventPublisherPort {
    private static final Logger log = LoggerFactory.getLogger(RabbitRouteEventPublisher.class);

    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitRouteEventPublisher(ObjectMapper objectMapper,
                                     RabbitTemplate rabbitTemplate,
                                     @Value("${rabbitmq.exchange:logistics.exchange}") String exchange,
                                     @Value("${rabbitmq.routing-key.route-completed:route.completed}") String routingKey) {
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public void publish(RouteCompletedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            log.info("[RabbitMQ] Published route.completed event: {}", payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize route event", ex);
        }
    }

    public void publishPayload(String payload) {
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("[RabbitMQ] Published outbox payload: {}", payload);
    }
}
