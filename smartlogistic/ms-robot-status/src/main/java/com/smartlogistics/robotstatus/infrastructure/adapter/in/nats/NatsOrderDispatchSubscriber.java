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
 * Inbound NATS adapter that subscribes to order.created events
 * and dispatches an available robot to pick the items.
 *
 * Infrastructure layer — framework imports allowed.
 * Delegates business logic through input/output ports (DIP).
 */
@Component
public class NatsOrderDispatchSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsOrderDispatchSubscriber.class);

    private final Connection nats;
    private final ObjectMapper mapper;
    private final DispatchRobotUseCase dispatchRobotUseCase;
    private final RobotDispatchPort robotDispatchPort;

    public NatsOrderDispatchSubscriber(Connection nats, ObjectMapper mapper,
                                       DispatchRobotUseCase dispatchRobotUseCase,
                                       RobotDispatchPort robotDispatchPort) {
        this.nats = nats;
        this.mapper = mapper;
        this.dispatchRobotUseCase = dispatchRobotUseCase;
        this.robotDispatchPort = robotDispatchPort;
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

                String robotId = dispatchRobotUseCase.findAvailableRobot();

                if (robotId != null) {
                    log.info("🤖 Dispatching robot {} for order #{}", robotId, orderId);
                    robotDispatchPort.sendGoToCommand(robotId, pickupSpot, orderId);
                    robotDispatchPort.publishOrderDispatched(orderId, robotId);
                } else {
                    log.warn("⚠️ No available robot for order #{}", orderId);
                }

            } catch (Exception e) {
                log.error("Error processing order event", e);
            }
        });

        d.subscribe("order.created");
        log.info("✅ NatsOrderDispatchSubscriber subscribed to order.created");
    }
}