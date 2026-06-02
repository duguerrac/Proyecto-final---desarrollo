package com.smartlogistics.robotstatus.infrastructure.adapter.out.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.RobotDispatchPort;
import com.smartlogistics.robotstatus.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ adapter that implements RobotDispatchPort for sending
 * robot commands and publishing order/package notifications.
 */
@Component
public class RabbitRobotDispatchAdapter implements RobotDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitRobotDispatchAdapter.class);
    private static final String RK_ORDER_STATUS = "order.status_changed";
    private static final String RK_PACKAGE_TAKEN = "package.taken";
    private static final String RK_PACKAGE_DELIVERED = "package.delivered";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitRobotDispatchAdapter(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
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
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.RK_ROBOT_COMMAND + "." + robotId, json);
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
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_ROBOT_COMMAND, json);
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
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RK_ORDER_STATUS, json);
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
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RK_PACKAGE_TAKEN, json);
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
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RK_PACKAGE_DELIVERED, json);
            log.info("📤 Published package.delivered for package #{}", packageId);
        } catch (Exception e) {
            log.error("Failed to publish package.delivered: {}", e.getMessage());
        }
    }
}