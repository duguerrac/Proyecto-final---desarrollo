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
 * Inbound RabbitMQ adapter that subscribes to order dispatched events
 * and dispatches an available robot to pick the items.
 */
@Component
public class RabbitOrderDispatchSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderDispatchSubscriber.class);

    private final ObjectMapper mapper;
    private final DispatchRobotUseCase dispatchRobotUseCase;
    private final RobotDispatchPort robotDispatchPort;

    public RabbitOrderDispatchSubscriber(ObjectMapper mapper,
                                         DispatchRobotUseCase dispatchRobotUseCase,
                                         RobotDispatchPort robotDispatchPort) {
        this.mapper = mapper;
        this.dispatchRobotUseCase = dispatchRobotUseCase;
        this.robotDispatchPort = robotDispatchPort;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ROBOT_DISPATCH_ORDER)
    public void onOrderDispatched(Message message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode order = mapper.readTree(body);
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
    }
}