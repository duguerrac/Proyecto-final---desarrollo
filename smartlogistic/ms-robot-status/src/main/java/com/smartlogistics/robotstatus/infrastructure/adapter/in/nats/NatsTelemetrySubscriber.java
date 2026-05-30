package com.smartlogistics.robotstatus.infrastructure.adapter.in.nats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes to per-robot telemetry from the UE5 simulation.
 * Updates battery levels in the cache and auto-sends low-battery robots
 * to charge via a RETURN_DOCK command.
 *
 * Flow:
 *   1. UE5 publishes smartlogistic.robot.telemetry.<robotId> every N seconds
 *   2. This subscriber reads batteryLevel from the telemetry
 *   3. If battery < 20% and robot is IDLE/available → mark as CHARGING,
 *      unavailable, and publish a RETURN_DOCK command back to UE5
 */
@Component
public class NatsTelemetrySubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsTelemetrySubscriber.class);
    private static final String TELEMETRY_SUBJECT = "smartlogistic.robot.telemetry.>";
    private static final String COMMAND_SUBJECT = "smartlogistic.robot.command";
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final Connection nats;
    private final ObjectMapper mapper;
    private final RobotCachePort robotCachePort;
    private final RobotEventPort robotEventPort;

    public NatsTelemetrySubscriber(Connection nats, ObjectMapper mapper,
                                   RobotCachePort robotCachePort, RobotEventPort robotEventPort) {
        this.nats = nats;
        this.mapper = mapper;
        this.robotCachePort = robotCachePort;
        this.robotEventPort = robotEventPort;
    }

    @PostConstruct
    public void subscribe() {
        Dispatcher d = nats.createDispatcher(msg -> {
            try {
                JsonNode root = mapper.readTree(msg.getData());
                JsonNode robotNode = root.has("robot") ? root.get("robot") : root;

                String robotId = robotNode.has("id") ? robotNode.get("id").asText() : "";
                int batteryLevel = robotNode.has("batteryLevel") ? robotNode.get("batteryLevel").asInt() : 100;
                String mode = robotNode.has("operationalMode") ? robotNode.get("operationalMode").asText() : "";

                if (robotId.isEmpty()) return;

                log.debug("🔋 Telemetry: robot={}, battery={}%, mode={}", robotId, batteryLevel, mode);

                // Update battery in cache
                robotCachePort.findById(robotId).ifPresent(robot -> {
                    int oldBattery = robot.getBatteryLevel();
                    robot.setBatteryLevel(batteryLevel);

                    // Auto-charge: if battery is low AND robot is idle/available
                    if (batteryLevel < LOW_BATTERY_THRESHOLD && robot.isAvailable()
                            && (robot.getOperationalMode() == RobotStatus.IDLE
                            || robot.getOperationalMode() == RobotStatus.MOVING)) {

                        log.info("🔋⚡ Robot {} battery LOW ({}%) — sending to charge station", robotId, batteryLevel);

                        robot.setAvailable(false);
                        robot.setOperationalMode(RobotStatus.CHARGING);
                        robotCachePort.save(robot);
                        robotEventPort.publishStatusUpdate(robot);

                        // Send RETURN_DOCK command to UE5 simulation
                        sendReturnDockCommand(robotId);
                    }
                    else {
                        // Just update battery in cache
                        robotCachePort.save(robot);

                        // Publish status update if battery changed significantly
                        if (Math.abs(oldBattery - batteryLevel) >= 5) {
                            robotEventPort.publishStatusUpdate(robot);
                        }
                    }
                });

            } catch (Exception e) {
                log.error("Error processing telemetry event: {}", e.getMessage());
            }
        });

        d.subscribe(TELEMETRY_SUBJECT);
        log.info("✅ NatsTelemetrySubscriber listening on {}", TELEMETRY_SUBJECT);
    }

    /**
     * Publish a RETURN_DOCK command so the UE5 simulation moves the robot to a charging station.
     */
    private void sendReturnDockCommand(String robotId) {
        try {
            String commandJson = mapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("robotId", robotId);
                put("commandType", "RETURN_DOCK");
                put("targetLocation", "");
            }});

            nats.publish(COMMAND_SUBJECT, commandJson.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published RETURN_DOCK command for robot {}", robotId);
        } catch (Exception e) {
            log.error("Failed to publish RETURN_DOCK for robot {}: {}", robotId, e.getMessage());
        }
    }
}