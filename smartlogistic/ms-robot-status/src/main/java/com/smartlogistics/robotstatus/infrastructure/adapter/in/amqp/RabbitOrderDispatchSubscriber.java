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
 * and dispatches an available robot to pick the items from the shelf
 * and deliver them to the delivery dock (STOCK_OUT mission).
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
            String pickupSpot = order.has("pickupSpotCode") && !order.get("pickupSpotCode").isNull()
                    ? order.get("pickupSpotCode").asText() : "";
            String deliveryPoint = order.has("deliveryPoint") && !order.get("deliveryPoint").isNull()
                    ? order.get("deliveryPoint").asText() : "DELIVERY-1";

            // Extract first line's SKU and quantity for the mission
            String itemSku = "";
            int quantity = 1;
            if (order.has("lines") && order.get("lines").isArray() && order.get("lines").size() > 0) {
                JsonNode firstLine = order.get("lines").get(0);
                itemSku = firstLine.has("sku") ? firstLine.get("sku").asText() : "";
                quantity = firstLine.has("quantity") ? firstLine.get("quantity").asInt() : 1;
            }

            log.info("📦 Order #{} received — pickup={}, delivery={}, sku={}, qty={}",
                    orderId, pickupSpot, deliveryPoint, itemSku, quantity);

            if (pickupSpot.isEmpty()) {
                log.warn("⚠️ Order #{} has no pickupSpotCode — cannot dispatch robot", orderId);
                return;
            }

            // Build mission data to store on the robot for HTTP polling fallback
            java.util.Map<String, Object> mission = new java.util.HashMap<>();
            mission.put("missionType", "STOCK_OUT");
            mission.put("orderId", orderId);
            mission.put("pickupSpotCode", pickupSpot);
            mission.put("deliverySpotCode", deliveryPoint);
            mission.put("itemSku", itemSku);
            mission.put("quantity", quantity);

            String robotId = dispatchRobotUseCase.findAvailableRobot(mission);

            if (robotId != null) {
                log.info("🤖 Dispatching robot {} for STOCK_OUT order #{} ({} x{} from {} → {})",
                        robotId, orderId, itemSku, quantity, pickupSpot, deliveryPoint);

                // Send a proper STOCK_OUT mission that Unreal can handle
                robotDispatchPort.sendStockOutMission(robotId, orderId,
                        pickupSpot, deliveryPoint, itemSku, quantity);
                robotDispatchPort.publishOrderDispatched(orderId, robotId);
            } else {
                log.warn("⚠️ No available robot for order #{}", orderId);
            }

        } catch (Exception e) {
            log.error("Error processing order event", e);
        }
    }
}