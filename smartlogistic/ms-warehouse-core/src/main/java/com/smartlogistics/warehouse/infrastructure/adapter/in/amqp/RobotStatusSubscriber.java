package com.smartlogistics.warehouse.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RobotJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RobotJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound RabbitMQ adapter that subscribes to robot status update events
 * from ms-robot-status and persists them to the warehouse database.
 * This ensures robot state survives restarts (Redis is volatile).
 */
@Component
public class RobotStatusSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RobotStatusSubscriber.class);

    private final ObjectMapper mapper;
    private final RobotJpaRepository robotRepo;

    public RobotStatusSubscriber(ObjectMapper mapper, RobotJpaRepository robotRepo) {
        this.mapper = mapper;
        this.robotRepo = robotRepo;
    }

    @RabbitListener(queues = "robot.status.update.wh")
    public void onRobotStatusUpdate(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode robotNode = root.has("robot") ? root.get("robot") : root;

            String robotId = robotNode.has("id") ? robotNode.get("id").asText() : "";
            if (robotId.isEmpty()) {
                log.warn("[RabbitMQ] robot.status.update without robot id — skipping");
                return;
            }

            String operationalMode = robotNode.has("operationalMode") ? robotNode.get("operationalMode").asText() : "IDLE";
            int batteryLevel = robotNode.has("batteryLevel") ? robotNode.get("batteryLevel").asInt() : 100;
            boolean available = robotNode.has("available") && robotNode.get("available").asBoolean();
            String currentLocation = robotNode.has("currentLocation") ? robotNode.get("currentLocation").asText() : "";
            String name = robotNode.has("name") ? robotNode.get("name").asText() : "";

            // Upsert: create if not exists, update if exists
            RobotJpaEntity entity = robotRepo.findById(robotId).orElseGet(() -> {
                RobotJpaEntity e = new RobotJpaEntity();
                e.setId(robotId);
                e.setName(name.isEmpty() ? robotId : name);
                return e;
            });

            // Only update fields that changed — skip no-op updates
            boolean changed = false;
            if (!operationalMode.equals(entity.getOperationalMode())) {
                entity.setOperationalMode(operationalMode);
                changed = true;
            }
            if (batteryLevel != entity.getBatteryLevel()) {
                entity.setBatteryLevel(batteryLevel);
                changed = true;
            }
            if (available != entity.isAvailable()) {
                entity.setAvailable(available);
                changed = true;
            }
            if (!currentLocation.equals(entity.getCurrentLocation())) {
                entity.setCurrentLocation(currentLocation);
                changed = true;
            }
            if (!name.isEmpty() && !name.equals(entity.getName())) {
                entity.setName(name);
                changed = true;
            }

            if (changed) {
                robotRepo.save(entity);
                log.info("[DB] Robot {} persisted: mode={}, battery={}%, available={}, loc={}",
                        robotId, operationalMode, batteryLevel, available, currentLocation);
            }
        } catch (Exception e) {
            log.error("[RabbitMQ] Error processing robot.status.update", e);
        }
    }
}
