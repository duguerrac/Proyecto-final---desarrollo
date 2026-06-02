package com.smartlogistics.robotstatus.infrastructure.adapter.out.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotCommandPort;
import com.smartlogistics.robotstatus.domain.model.RobotCommand;
import com.smartlogistics.robotstatus.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ adapter for publishing robot commands.
 */
@Component
public class RabbitRobotCommandPublisher implements RobotCommandPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitRobotCommandPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitRobotCommandPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishCommand(RobotCommand command) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "ROBOT_COMMAND");
            event.put("timestamp", Instant.now().toString());
            event.put("source", "ms-robot-status");
            event.put("robotId", command.getRobotId());
            event.put("commandType", command.getType().name());
            event.put("targetLocation", command.getTargetLocation());
            event.put("routePoints", command.getRoutePoints());
            event.put("itemSku", command.getItemSku());
            event.put("orderId", command.getOrderId());

            String json = objectMapper.writeValueAsString(event);
            String routingKey = RabbitMQConfig.RK_ROBOT_COMMAND + "." + command.getRobotId();
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, json);
            log.info("Published COMMAND {} for robot {} to exchange {}",
                    command.getType(), command.getRobotId(), RabbitMQConfig.EXCHANGE);
        } catch (Exception e) {
            log.error("Failed to publish COMMAND for robot {}: {}", command.getRobotId(), e.getMessage());
        }
    }
}