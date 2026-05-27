package com.smartlogistics.robotstatus.infrastructure.adapter.out.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NATS adapter that publishes robot status events.
 * Infrastructure layer — framework imports allowed.
 * Implements the hexagonal output port (RobotEventPort).
 */
@Component
public class NatsRobotEventAdapter implements RobotEventPort {

    private static final Logger log = LoggerFactory.getLogger(NatsRobotEventAdapter.class);
    private static final String SUBJECT_UPDATE = "smartlogistic.robot.status.update";
    private static final String SUBJECT_BATCH = "smartlogistic.robot.status.batch";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public NatsRobotEventAdapter(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishStatusUpdate(Robot robot) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "STATUS_UPDATE");
            event.put("timestamp", Instant.now().toString());
            event.put("source", "ms-robot-status");
            event.put("robot", toRobotMap(robot));

            String json = objectMapper.writeValueAsString(event);
            natsConnection.publish(SUBJECT_UPDATE, json.getBytes());
            log.info("Published STATUS_UPDATE for robot {} to subject {}", robot.getId(), SUBJECT_UPDATE);
        } catch (Exception e) {
            log.error("Failed to publish STATUS_UPDATE for robot {}: {}", robot.getId(), e.getMessage());
        }
    }

    @Override
    public void publishStatusBatch(List<Robot> robots) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "STATUS_BATCH");
            event.put("timestamp", Instant.now().toString());
            event.put("source", "ms-robot-status");
            event.put("count", robots.size());
            event.put("robots", robots.stream().map(this::toRobotMap).toList());

            String json = objectMapper.writeValueAsString(event);
            natsConnection.publish(SUBJECT_BATCH, json.getBytes());
            log.debug("Published STATUS_BATCH with {} robots", robots.size());
        } catch (Exception e) {
            log.error("Failed to publish STATUS_BATCH: {}", e.getMessage());
        }
    }

    private Map<String, Object> toRobotMap(Robot robot) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", robot.getId());
        map.put("name", robot.getName());
        map.put("batteryLevel", robot.getBatteryLevel());
        map.put("available", robot.isAvailable());
        map.put("currentLocation", robot.getCurrentLocation());
        map.put("operationalMode", robot.getOperationalMode() != null
                ? robot.getOperationalMode().name() : null);
        map.put("assignable", robot.isAssignable());
        return map;
    }
}