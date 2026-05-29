package com.smartlogistics.robotstatus.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes to package.received NATS events and dispatches
 * an available robot to transport the package from reception to target spot.
 *
 * Flow:
 *   1. warehouse-core publishes smartlogistic.package.received
 *   2. This service picks an available robot and publishes
 *      smartlogistic.robot.command with mission data (STOCK_IN)
 *   3. UE5 simulation receives the command, moves robot in two legs:
 *      a) Robot → Reception spot: publishes smartlogistic.mission.completed (PACKAGE_PICKED)
 *      b) Robot → Target spot: publishes smartlogistic.mission.completed (PACKAGE_DELIVERED)
 *   4. This service translates those events into package.taken / package.delivered
 *      for warehouse-core to update DB (status changes, inventory updates)
 */
@Service
public class PackageDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PackageDispatchService.class);

    private final Connection nats;
    private final ObjectMapper mapper;
    private final RobotStatusService robotStatusService;

    public PackageDispatchService(Connection nats, ObjectMapper mapper, RobotStatusService robotStatusService) {
        this.nats = nats;
        this.mapper = mapper;
        this.robotStatusService = robotStatusService;
    }

    @PostConstruct
    public void subscribe() {
        subscribeToPackageReceived();
        subscribeToMissionCompleted();
    }

    private void subscribeToPackageReceived() {
        Dispatcher d = nats.createDispatcher(msg -> {
            try {
                JsonNode pkg = mapper.readTree(msg.getData());
                long packageId = pkg.get("packageId").asLong();
                String sku = pkg.has("sku") ? pkg.get("sku").asText() : "";
                String receptionSpot = pkg.has("receptionSpotCode") ? pkg.get("receptionSpotCode").asText() : "";
                String targetSpot = pkg.has("targetSpotCode") ? pkg.get("targetSpotCode").asText() : "";
                long itemId = pkg.has("itemId") ? pkg.get("itemId").asLong() : 0;
                int quantity = pkg.has("quantity") ? pkg.get("quantity").asInt() : 1;

                log.info("📦 Package #{} received (SKU={}) — reception={}, target={}, item={}, qty={}",
                        packageId, sku, receptionSpot, targetSpot, itemId, quantity);

                String robotId = robotStatusService.findAvailableRobot();

                if (robotId != null) {
                    dispatchRobotForStockIn(robotId, packageId, sku, receptionSpot, targetSpot, itemId, quantity);
                } else {
                    log.warn("⚠️ No available robot for package #{} — package will wait", packageId);
                }

            } catch (Exception e) {
                log.error("Error processing package.received event", e);
            }
        });

        d.subscribe("smartlogistic.package.received");
        log.info("✅ PackageDispatchService subscribed to smartlogistic.package.received");
    }

    private void subscribeToMissionCompleted() {
        Dispatcher d = nats.createDispatcher(msg -> {
            try {
                JsonNode event = mapper.readTree(msg.getData());
                String eventType = event.has("event") ? event.get("event").asText() : "";
                String robotId = event.has("robotId") ? event.get("robotId").asText() : "";
                String packageId = event.has("packageId") ? event.get("packageId").asText() : "";
                String spotCode = event.has("spotCode") ? event.get("spotCode").asText() : "";
                String itemId = event.has("itemId") ? event.get("itemId").asText() : "";
                int quantity = event.has("quantity") ? event.get("quantity").asInt() : 0;

                log.info("🏁 Mission event: type={}, robot={}, package={}, spot={}",
                        eventType, robotId, packageId, spotCode);

                switch (eventType) {
                    case "PACKAGE_PICKED":
                        // Robot picked up package at reception → notify warehouse-core
                        publishPackageTaken(packageId, robotId);
                        break;

                    case "PACKAGE_DELIVERED":
                        // Robot delivered package at target spot → notify warehouse-core
                        publishPackageDelivered(packageId);
                        // Mark robot as available again
                        robotStatusService.markRobotAvailable(robotId);
                        log.info("✅ Robot {} is now available again", robotId);
                        break;

                    default:
                        log.warn("Unknown mission event type: {}", eventType);
                }

            } catch (Exception e) {
                log.error("Error processing mission.completed event", e);
            }
        });

        d.subscribe("smartlogistic.mission.completed");
        log.info("✅ PackageDispatchService subscribed to smartlogistic.mission.completed");
    }

    /**
     * Publishes a STOCK_IN mission command on the shared subject that the UE5 simulation listens to.
     */
    private void dispatchRobotForStockIn(String robotId, long packageId, String sku,
                                          String receptionSpot, String targetSpot,
                                          long itemId, int quantity) {
        try {
            log.info("🤖 Dispatching robot {} for package #{} (STOCK_IN → {})", robotId, packageId, targetSpot);

            String missionJson = mapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("robotId", robotId);
                put("missionType", "STOCK_IN");
                put("packageId", String.valueOf(packageId));
                put("sku", sku);
                put("receptionSpotCode", receptionSpot);
                put("targetSpotCode", targetSpot);
                put("itemId", String.valueOf(itemId));
                put("quantity", quantity);
            }});

            nats.publish("smartlogistic.robot.command", missionJson.getBytes(StandardCharsets.UTF_8));
            log.info("✅ Robot {} dispatched for STOCK_IN package #{} → spot {}", robotId, packageId, targetSpot);

        } catch (Exception e) {
            log.error("Failed to dispatch robot {} for package #{}", robotId, packageId, e);
        }
    }

    /**
     * Translate PACKAGE_PICKED → package.taken (for warehouse-core PackageController)
     */
    private void publishPackageTaken(String packageId, String robotId) {
        try {
            String json = String.format("{\"packageId\":%s,\"robotId\":\"%s\"}", packageId, robotId);
            nats.publish("package.taken", json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published package.taken for package #{} (robot={})", packageId, robotId);
        } catch (Exception e) {
            log.error("Failed to publish package.taken", e);
        }
    }

    /**
     * Translate PACKAGE_DELIVERED → package.delivered (for warehouse-core PackageController)
     */
    private void publishPackageDelivered(String packageId) {
        try {
            String json = String.format("{\"packageId\":%s}", packageId);
            nats.publish("package.delivered", json.getBytes(StandardCharsets.UTF_8));
            log.info("📤 Published package.delivered for package #{}", packageId);
        } catch (Exception e) {
            log.error("Failed to publish package.delivered", e);
        }
    }
}