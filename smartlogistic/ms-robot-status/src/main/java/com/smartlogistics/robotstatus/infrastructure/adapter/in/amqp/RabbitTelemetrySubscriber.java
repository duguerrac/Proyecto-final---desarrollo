package com.smartlogistics.robotstatus.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;
import com.smartlogistics.robotstatus.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Subscribes to per-robot telemetry from the UE5 simulation via RabbitMQ.
 * Updates battery levels in the cache and auto-sends low-battery robots
 * to charge via a RETURN_DOCK command.
 */
@Component
public class RabbitTelemetrySubscriber {

    private static final Logger log = LoggerFactory.getLogger(RabbitTelemetrySubscriber.class);
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper;
    private final RobotCachePort robotCachePort;
    private final RobotEventPort robotEventPort;

    public RabbitTelemetrySubscriber(RabbitTemplate rabbitTemplate, ObjectMapper mapper,
                                     RobotCachePort robotCachePort, RobotEventPort robotEventPort) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.robotCachePort = robotCachePort;
        this.robotEventPort = robotEventPort;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ROBOT_TELEMETRY)
    public void onTelemetry(Message message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(body);
            JsonNode robotNode = root.has("robot") ? root.get("robot") : root;

            String robotId = robotNode.has("id") ? robotNode.get("id").asText() : "";
            int batteryLevel = robotNode.has("batteryLevel") ? robotNode.get("batteryLevel").asInt() : 100;
            String mode = robotNode.has("operationalMode") ? robotNode.get("operationalMode").asText() : "";

            if (robotId.isEmpty()) return;

            log.debug("🔋 Telemetry: robot={}, battery={}%, mode={}", robotId, batteryLevel, mode);

            robotCachePort.findById(robotId).ifPresent(robot -> {
                int oldBattery = robot.getBatteryLevel();
                robot.setBatteryLevel(batteryLevel);

                if (batteryLevel < LOW_BATTERY_THRESHOLD && robot.isAvailable()
                        && (robot.getOperationalMode() == RobotStatus.IDLE
                        || robot.getOperationalMode() == RobotStatus.MOVING)) {

                    log.info("🔋⚡ Robot {} battery LOW ({}%) — sending to charge station", robotId, batteryLevel);

                    robot.setAvailable(false);
                    robot.setOperationalMode(RobotStatus.CHARGING);
                    robotCachePort.save(robot);
                    robotEventPort.publishStatusUpdate(robot);

                    sendReturnDockCommand(robotId);
                } else {
                    robotCachePort.save(robot);

                    if (Math.abs(oldBattery - batteryLevel) >= 5) {
                        robotEventPort.publishStatusUpdate(robot);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Error processing telemetry event: {}", e.getMessage());
        }
    }

    private void sendReturnDockCommand(String robotId) {
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("robotId", robotId);
            command.put("commandType", "RETURN_DOCK");
            command.put("targetLocation", "");

            String json = mapper.writeValueAsString(command);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_ROBOT_COMMAND, json);
            log.info("📤 Published RETURN_DOCK command for robot {}", robotId);
        } catch (Exception e) {
            log.error("Failed to publish RETURN_DOCK for robot {}: {}", robotId, e.getMessage());
        }
    }
}