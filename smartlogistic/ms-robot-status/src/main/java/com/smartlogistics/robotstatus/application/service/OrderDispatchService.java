package com.smartlogistics.robotstatus.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Subscribes to order.created NATS events and dispatches
 * an available robot to pick the items.
 */
@Service
public class OrderDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OrderDispatchService.class);

    private final Connection nats;
    private final ObjectMapper mapper;
    private final RobotStatusService robotStatusService;

    public OrderDispatchService(Connection nats, ObjectMapper mapper, RobotStatusService robotStatusService) {
        this.nats = nats;
        this.mapper = mapper;
        this.robotStatusService = robotStatusService;
    }

    @PostConstruct
    public void subscribe() {
        Dispatcher d = nats.createDispatcher(msg -> {
            try {
                JsonNode order = mapper.readTree(msg.getData());
                long orderId = order.get("orderId").asLong();
                String pickupSpot = order.has("pickupSpotCode") ? order.get("pickupSpotCode").asText() : "";
                String deliveryPoint = order.has("deliveryPoint") ? order.get("deliveryPoint").asText() : "";

                log.info("📦 Order #{} received — pickup={}, delivery={}", orderId, pickupSpot, deliveryPoint);

                // Find an available robot (IDLE status)
                String robotId = robotStatusService.findAvailableRobot();

                if (robotId != null) {
                    log.info("🤖 Dispatching robot {} for order #{}", robotId, orderId);

                    // Send GOTO command to the robot via NATS
                    String command = String.format(
                        "{\"robotId\":\"%s\",\"command\":\"GOTO\",\"target\":\"%s\",\"orderId\":%d}",
                        robotId, pickupSpot, orderId);
                    nats.publish("robot.command." + robotId, command.getBytes());

                    // Notify order service that robot was assigned
                    String assignEvent = String.format(
                        "{\"orderId\":%d,\"status\":\"DISPATCHED\",\"robotId\":\"%s\"}",
                        orderId, robotId);
                    nats.publish("order.status_changed", assignEvent.getBytes());
                } else {
                    log.warn("⚠️ No available robot for order #{}", orderId);
                }

            } catch (Exception e) {
                log.error("Error processing order event", e);
            }
        });

        d.subscribe("order.created");
        log.info("✅ OrderDispatchService subscribed to order.created");
    }
}