package com.smartlogistics.robotstatus.infrastructure.adapter.in.nats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.in.DispatchRobotUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotDispatchPort;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Inbound NATS adapter that subscribes to package.received and
 * mission.completed events to dispatch robots for package transport.
 *
 * Infrastructure layer — framework imports allowed.
 * Delegates business logic through input/output ports (DIP).
 */
@Component
public class NatsPackageDispatchSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsPackageDispatchSubscriber.class);

    private final Connection nats;
    private final ObjectMapper mapper;
    private final DispatchRobotUseCase dispatchRobotUseCase;
    private final RobotDispatchPort robotDispatchPort;

    public NatsPackageDispatchSubscriber(Connection nats, ObjectMapper mapper,
                                         DispatchRobotUseCase dispatchRobotUseCase,
                                         RobotDispatchPort robotDispatchPort) {
        this.nats = nats;
        this.mapper = mapper;
        this.dispatchRobotUseCase = dispatchRobotUseCase;
        this.robotDispatchPort = robotDispatchPort;
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

                String robotId = dispatchRobotUseCase.findAvailableRobot();

                if (robotId != null) {
                    log.info("🤖 Dispatching robot {} for package #{} (STOCK_IN → {})",
                            robotId, packageId, targetSpot);
                    robotDispatchPort.sendStockInMission(robotId, packageId, sku,
                            receptionSpot, targetSpot, itemId, quantity);
                } else {
                    log.warn("⚠️ No available robot for package #{} — package will wait", packageId);
                }

            } catch (Exception e) {
                log.error("Error processing package.received event", e);
            }
        });

        d.subscribe("smartlogistic.package.received");
        log.info("✅ NatsPackageDispatchSubscriber subscribed to smartlogistic.package.received");
    }

    private void subscribeToMissionCompleted() {
        Dispatcher d = nats.createDispatcher(msg -> {
            try {
                JsonNode event = mapper.readTree(msg.getData());
                String eventType = event.has("event") ? event.get("event").asText() : "";
                String robotId = event.has("robotId") ? event.get("robotId").asText() : "";
                String packageId = event.has("packageId") ? event.get("packageId").asText() : "";

                log.info("🏁 Mission event: type={}, robot={}, package={}", eventType, robotId, packageId);

                switch (eventType) {
                    case "PACKAGE_PICKED" -> {
                        robotDispatchPort.publishPackageTaken(packageId, robotId);
                    }
                    case "PACKAGE_DELIVERED" -> {
                        robotDispatchPort.publishPackageDelivered(packageId);
                        dispatchRobotUseCase.markRobotAvailable(robotId);
                        log.info("✅ Robot {} is now available again", robotId);
                    }
                    default -> log.warn("Unknown mission event type: {}", eventType);
                }

            } catch (Exception e) {
                log.error("Error processing mission.completed event", e);
            }
        });

        d.subscribe("smartlogistic.mission.completed");
        log.info("✅ NatsPackageDispatchSubscriber subscribed to smartlogistic.mission.completed");
    }
}