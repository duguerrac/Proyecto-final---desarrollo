package com.smartlogistics.robotstatus.infrastructure.adapter.out.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotCommandPort;
import com.smartlogistics.robotstatus.domain.model.RobotCommand;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * NATS adapter for publishing robot commands.
 * Single responsibility: command publishing only.
 * Infrastructure layer — framework imports allowed.
 */
@Component
public class NatsRobotCommandPublisher implements RobotCommandPort {

    private static final Logger log = LoggerFactory.getLogger(NatsRobotCommandPublisher.class);
    private static final String SUBJECT_COMMAND = "smartlogistic.robot.command";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public NatsRobotCommandPublisher(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishCommand(RobotCommand command) {
        try {
            String subject = SUBJECT_COMMAND + "." + command.getRobotId();

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
            natsConnection.publish(subject, json.getBytes());
            log.info("Published COMMAND {} for robot {} to subject {}",
                    command.getType(), command.getRobotId(), subject);
        } catch (Exception e) {
            log.error("Failed to publish COMMAND for robot {}: {}", command.getRobotId(), e.getMessage());
        }
    }
}