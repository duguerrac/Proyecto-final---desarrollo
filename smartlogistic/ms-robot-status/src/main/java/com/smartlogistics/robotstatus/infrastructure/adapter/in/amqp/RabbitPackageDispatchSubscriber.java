package com.smartlogistics.robotstatus.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.in.DispatchRobotUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotDispatchPort;
import com.smartlogistics.robotstatus.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Inbound RabbitMQ adapter that subscribes to package dispatched events
 * and mission completed events to dispatch robots for package transport.
 */
@Component
public class RabbitPackageDispatchSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RabbitPackageDispatchSubscriber.class);

    private final ObjectMapper mapper;
    private final DispatchRobotUseCase dispatchRobotUseCase;
    private final RobotDispatchPort robotDispatchPort;

    public RabbitPackageDispatchSubscriber(ObjectMapper mapper,
                                           DispatchRobotUseCase dispatchRobotUseCase,
                                           RobotDispatchPort robotDispatchPort) {
        this.mapper = mapper;
        this.dispatchRobotUseCase = dispatchRobotUseCase;
        this.robotDispatchPort = robotDispatchPort;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ROBOT_DISPATCH_PACKAGE)
    public void onPackageDispatched(Message message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(body);

            // Detect event type: "package.received" or "mission.completed"
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";

            if ("MISSION_COMPLETED".equals(eventType)) {
                handleMissionCompleted(root);
            } else {
                handlePackageReceived(root);
            }

        } catch (Exception e) {
            log.error("Error processing package dispatch event", e);
        }
    }

    private void handlePackageReceived(JsonNode pkg) {
        long packageId = pkg.has("packageId") ? pkg.get("packageId").asLong() : 0;
        String sku = pkg.has("sku") ? pkg.get("sku").asText() : "";
        String receptionSpot = pkg.has("receptionSpotCode") ? pkg.get("receptionSpotCode").asText() : "";
        String targetSpot = pkg.has("targetSpotCode") ? pkg.get("targetSpotCode").asText() : "";
        long itemId = pkg.has("itemId") ? pkg.get("itemId").asLong() : 0;
        int quantity = pkg.has("quantity") ? pkg.get("quantity").asInt() : 1;

        log.info("📦 Package #{} received (SKU={}) — reception={}, target={}, item={}, qty={}",
                packageId, sku, receptionSpot, targetSpot, itemId, quantity);

        if (receptionSpot.isEmpty() || targetSpot.isEmpty()) {
            log.warn("⚠️ Package #{} missing reception/target spot — cannot dispatch robot", packageId);
            return;
        }

        // Build mission data to store on the robot for HTTP polling fallback
        java.util.Map<String, Object> mission = new java.util.HashMap<>();
        mission.put("missionType", "STOCK_IN");
        mission.put("packageId", packageId);
        mission.put("receptionSpotCode", receptionSpot);
        mission.put("targetSpotCode", targetSpot);
        mission.put("itemSku", sku);
        mission.put("itemId", itemId);
        mission.put("quantity", quantity);

        String robotId = dispatchRobotUseCase.findAvailableRobot(mission);

        if (robotId != null) {
            log.info("🤖 Dispatching robot {} for STOCK_IN package #{} ({} x{} from {} → {})",
                    robotId, packageId, sku, quantity, receptionSpot, targetSpot);

            // Send STOCK_IN mission via STOMP (primary) and store on robot for HTTP fallback
            robotDispatchPort.sendStockInMission(robotId, packageId, sku,
                    receptionSpot, targetSpot, itemId, quantity);
        } else {
            log.warn("⚠️ No available robot for package #{}", packageId);
        }
    }

    private void handleMissionCompleted(JsonNode event) {
        String missionEvent = event.has("event") ? event.get("event").asText() : "";
        String robotId = event.has("robotId") ? event.get("robotId").asText() : "";
        String packageId = event.has("packageId") ? event.get("packageId").asText() : "";
        String missionType = event.has("missionType") ? event.get("missionType").asText() : "";
        String spotCode = event.has("spotCode") ? event.get("spotCode").asText() : "";

        log.info("🏁 Mission event: type={}, robot={}, package={}, missionType={}", missionEvent, robotId, packageId, missionType);

        switch (missionEvent) {
            case "PACKAGE_PICKED" -> {
                robotDispatchPort.publishPackageTaken(packageId, robotId);
            }
            case "PACKAGE_DELIVERED" -> {
                robotDispatchPort.publishPackageDelivered(packageId, missionType, spotCode);
                dispatchRobotUseCase.markRobotAvailable(robotId);
                log.info("✅ Robot {} is now available again", robotId);
            }
            default -> log.warn("Unknown mission event type: {}", missionEvent);
        }
    }
}