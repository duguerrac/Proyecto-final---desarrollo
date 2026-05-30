package com.smartlogistics.robotstatus.infrastructure.adapter.out.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.nats.client.Connection;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * NATS adapter that implements RobotDispatchPort for sending
 * robot commands and publishing order/package notifications.
 * Infrastructure layer — framework imports allowed.
 */
@Component
public class NatsRobotDispatchAdapter implements RobotDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(NatsRobotDispatchAdapter.class);
    private static final String ROBOT_COMMAND_SUBJECT = "smartlogistic.robot.command";
    private static final String ORDER_STATUS_SUBJECT = "order.status_changed";
    private static final String PACKAGE_TAKEN_SUBJECT = "package.taken";
    private static final String PACKAGE_DELIVERED_SUBJECT = "package.delivered";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public NatsRobotDispatchAdapter(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendGoToCommand(String robotId, String target, long orderId) {
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("robotId", robotId);
            command.put("command", "GOTO");
            command.put("target", target);
            command.put("orderId", orderId);

            String json = objectMapper.writeValueAsString(command);
            natsConnection.publish("robot.command." + robotId, json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published GOTO command for robot {} → {}", robotId, target);
        } catch (Exception e) {
            log.error("Failed to publish GOTO command for robot {}: {}", robotId, e.getMessage());
        }
    }

    @Override
    public void sendStockInMission(String robotId, long packageId, String sku,
                                   String receptionSpot, String targetSpot,
                                   long itemId, int quantity) {
        try {
            Map<String, Object> mission = new HashMap<>();
            mission.put("robotId", robotId);
            mission.put("missionType", "STOCK_IN");
            mission.put("packageId", String.valueOf(packageId));
            mission.put("sku", sku);
            mission.put("receptionSpotCode", receptionSpot);
            mission.put("targetSpotCode", targetSpot);
            mission.put("itemId", String.valueOf(itemId));
            mission.put("quantity", quantity);

            String json = objectMapper.writeValueAsString(mission);
            natsConnection.publish(ROBOT_COMMAND_SUBJECT, json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published STOCK_IN mission for robot {} (package #{}) → spot {}",
                    robotId, packageId, targetSpot);
        } catch (Exception e) {
            log.error("Failed to publish STOCK_IN mission for robot {}: {}", robotId, e.getMessage());
        }
    }

    @Override
    public void publishOrderDispatched(long orderId, String robotId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", orderId);
            event.put("status", "DISPATCHED");
            event.put("robotId", robotId);

            String json = objectMapper.writeValueAsString(event);
            natsConnection.publish(ORDER_STATUS_SUBJECT, json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published order.dispatched for order #{} (robot={})", orderId, robotId);
        } catch (Exception e) {
            log.error("Failed to publish order dispatched: {}", e.getMessage());
        }
    }

    @Override
    public void publishPackageTaken(String packageId, String robotId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("packageId", packageId);
            event.put("robotId", robotId);

            String json = objectMapper.writeValueAsString(event);
            natsConnection.publish(PACKAGE_TAKEN_SUBJECT, json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published package.taken for package #{} (robot={})", packageId, robotId);
        } catch (Exception e) {
            log.error("Failed to publish package.taken: {}", e.getMessage());
        }
    }

    @Override
    public void publishPackageDelivered(String packageId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("packageId", packageId);

            String json = objectMapper.writeValueAsString(event);
            natsConnection.publish(PACKAGE_DELIVERED_SUBJECT, json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published package.delivered for package #{}", packageId);
        } catch (Exception e) {
            log.error("Failed to publish package.delivered: {}", e.getMessage());
        }
    }
}