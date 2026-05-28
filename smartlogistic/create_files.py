import os

base = r"d:\University\Proyecto final\Proyecto-final---desarrollo\smartlogistic\ms-robot-status\src\main\java\com\smartlogistics\robotstatus"

files = {}

files["infrastructure/adapter/out/redis/RedisWarehouseLayoutAdapter.java"] = """package com.smartlogistics.robotstatus.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.WarehouseLayoutPort;
import com.smartlogistics.robotstatus.domain.model.WarehouseLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RedisWarehouseLayoutAdapter implements WarehouseLayoutPort {

    private static final Logger log = LoggerFactory.getLogger(RedisWarehouseLayoutAdapter.class);
    private static final String LAYOUT_KEY = "warehouse:layout";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private List<WarehouseLocation> cachedLayout = new ArrayList<>();
    private Map<String, WarehouseLocation> nameIndex = new HashMap<>();
    private Map<String, List<WarehouseLocation>> typeIndex = new HashMap<>();

    public RedisWarehouseLayoutAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        loadFromRedis();
    }

    @Override
    public void saveLayout(List<WarehouseLocation> locations) {
        try {
            String json = objectMapper.writeValueAsString(locations);
            redis.opsForValue().set(LAYOUT_KEY, json);
            rebuildIndexes(locations);
            log.info("[WarehouseLayout] Saved {} locations to Redis", locations.size());
        } catch (Exception e) {
            log.error("[WarehouseLayout] Failed to save layout: {}", e.getMessage());
        }
    }

    @Override
    public List<WarehouseLocation> getLayout() {
        return Collections.unmodifiableList(cachedLayout);
    }

    @Override
    public Optional<WarehouseLocation> findByName(String name) {
        return Optional.ofNullable(nameIndex.get(name));
    }

    @Override
    public List<WarehouseLocation> findByType(String type) {
        return typeIndex.getOrDefault(type, Collections.emptyList());
    }

    @Override
    public boolean hasLayout() {
        return !cachedLayout.isEmpty();
    }

    private void loadFromRedis() {
        try {
            String json = redis.opsForValue().get(LAYOUT_KEY);
            if (json != null) {
                List<WarehouseLocation> locations = objectMapper.readValue(json,
                        new TypeReference<List<WarehouseLocation>>() {});
                rebuildIndexes(locations);
                log.info("[WarehouseLayout] Loaded {} locations from Redis", locations.size());
            }
        } catch (Exception e) {
            log.warn("[WarehouseLayout] No layout in Redis yet: {}", e.getMessage());
        }
    }

    private void rebuildIndexes(List<WarehouseLocation> locations) {
        this.cachedLayout = new ArrayList<>(locations);
        this.nameIndex = new HashMap<>();
        this.typeIndex = new HashMap<>();
        for (WarehouseLocation loc : locations) {
            nameIndex.put(loc.getName(), loc);
            typeIndex.computeIfAbsent(loc.getType(), k -> new ArrayList<>()).add(loc);
        }
    }
}
"""

files["infrastructure/adapter/in/nats/WarehouseLayoutListener.java"] = """package com.smartlogistics.robotstatus.infrastructure.adapter.in.nats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.robotstatus.application.port.out.WarehouseLayoutPort;
import com.smartlogistics.robotstatus.domain.model.WarehouseLocation;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WarehouseLayoutListener {

    private static final Logger log = LoggerFactory.getLogger(WarehouseLayoutListener.class);
    private static final String LAYOUT_SUBJECT = "smartlogistic.warehouse.layout";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final WarehouseLayoutPort layoutPort;

    public WarehouseLayoutListener(Connection natsConnection, ObjectMapper objectMapper, WarehouseLayoutPort layoutPort) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.layoutPort = layoutPort;
    }

    @PostConstruct
    public void subscribe() {
        Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                String json = new String(msg.getData());
                List<WarehouseLocation> locations = objectMapper.readValue(json,
                        new TypeReference<List<WarehouseLocation>>() {});
                layoutPort.saveLayout(locations);
                log.info("[LayoutListener] Received and stored warehouse layout with {} locations", locations.size());
            } catch (Exception e) {
                log.error("[LayoutListener] Failed to parse layout: {}", e.getMessage());
            }
        });
        dispatcher.subscribe(LAYOUT_SUBJECT);
        log.info("[LayoutListener] Subscribed to {}", LAYOUT_SUBJECT);
    }
}
"""

files["application/service/MissionOptimizerService.java"] = """package com.smartlogistics.robotstatus.application.service;

import com.smartlogistics.robotstatus.application.port.out.WarehouseLayoutPort;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotCommandPort;
import com.smartlogistics.robotstatus.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MissionOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(MissionOptimizerService.class);

    private final WarehouseLayoutPort layoutPort;
    private final RobotCachePort robotCachePort;
    private final RobotCommandPort commandPort;

    public MissionOptimizerService(WarehouseLayoutPort layoutPort, RobotCachePort robotCachePort, RobotCommandPort commandPort) {
        this.layoutPort = layoutPort;
        this.robotCachePort = robotCachePort;
        this.commandPort = commandPort;
    }

    /**
     * Create and dispatch an optimized mission to a robot.
     * Steps: GO_TO each pick location (TSP-ordered) -> PICK_UP -> GO_TO drop-off -> DROP_OFF -> RETURN_DOCK
     */
    public RobotMission createMission(String robotId, List<String> pickLocationNames,
                                       String dropOffLocationName, String orderId) {
        // Resolve locations
        List<WarehouseLocation> pickLocations = new ArrayList<>();
        for (String name : pickLocationNames) {
            WarehouseLocation loc = layoutPort.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found: " + name));
            pickLocations.add(loc);
        }

        WarehouseLocation dropOffLocation = layoutPort.findByName(dropOffLocationName)
                .orElseThrow(() -> new IllegalArgumentException("Drop-off location not found: " + dropOffLocationName));

        // Get robot's current position
        Robot robot = robotCachePort.findById(robotId)
                .orElseThrow(() -> new IllegalArgumentException("Robot not found: " + robotId));

        WarehouseLocation robotPos = layoutPort.findByName(robot.getCurrentLocation())
                .orElseGet(() -> new WarehouseLocation("CURRENT", "UNKNOWN", 0, 0, 0));

        // Optimize pick order with nearest-neighbor TSP
        List<WarehouseLocation> optimizedPicks = optimizePickOrder(robotPos, pickLocations);

        // Build mission steps
        RobotMission mission = new RobotMission();
        mission.setRobotId(robotId);
        mission.setOrderId(orderId);

        int stepIdx = 0;
        WarehouseLocation currentPos = robotPos;

        for (WarehouseLocation pickLoc : optimizedPicks) {
            // GO_TO pick location
            mission.getSteps().add(new RobotMission.MissionStep(stepIdx++, "GO_TO", pickLoc.getName(),
                    pickLoc.getX(), pickLoc.getY(), pickLoc.getZ()));
            // PICK_UP at location
            mission.getSteps().add(new RobotMission.MissionStep(stepIdx++, "PICK_UP", pickLoc.getName(),
                    pickLoc.getX(), pickLoc.getY(), pickLoc.getZ()));
            currentPos = pickLoc;
        }

        // GO_TO drop-off
        mission.getSteps().add(new RobotMission.MissionStep(stepIdx++, "GO_TO", dropOffLocation.getName(),
                dropOffLocation.getX(), dropOffLocation.getY(), dropOffLocation.getZ()));
        // DROP_OFF
        mission.getSteps().add(new RobotMission.MissionStep(stepIdx++, "DROP_OFF", dropOffLocation.getName(),
                dropOffLocation.getX(), dropOffLocation.getY(), dropOffLocation.getZ()));

        // RETURN_DOCK - find nearest charging station
        List<WarehouseLocation> chargers = layoutPort.findByType("CHARGING");
        if (!chargers.isEmpty()) {
            WarehouseLocation nearestCharger = findNearest(dropOffLocation, chargers);
            mission.getSteps().add(new RobotMission.MissionStep(stepIdx++, "RETURN_DOCK", nearestCharger.getName(),
                    nearestCharger.getX(), nearestCharger.getY(), nearestCharger.getZ()));
        }

        mission.setStatus(RobotMission.MissionStatus.PENDING);

        // Publish the full mission to NATS for UE5
        publishMission(mission);

        log.info("[MissionOptimizer] Created mission {} for robot {} with {} steps",
                mission.getMissionId(), robotId, mission.getSteps().size());

        return mission;
    }

    /**
     * Nearest-neighbor heuristic for TSP on pick locations.
     */
    private List<WarehouseLocation> optimizePickOrder(WarehouseLocation start, List<WarehouseLocation> picks) {
        List<WarehouseLocation> remaining = new ArrayList<>(picks);
        List<WarehouseLocation> ordered = new ArrayList<>();
        WarehouseLocation current = start;

        while (!remaining.isEmpty()) {
            WarehouseLocation nearest = findNearest(current, remaining);
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return ordered;
    }

    private WarehouseLocation findNearest(WarehouseLocation from, List<WarehouseLocation> candidates) {
        WarehouseLocation nearest = candidates.get(0);
        double minDist = from.distanceTo(nearest);
        for (WarehouseLocation loc : candidates) {
            double dist = from.distanceTo(loc);
            if (dist < minDist) {
                minDist = dist;
                nearest = loc;
            }
        }
        return nearest;
    }

    private void publishMission(RobotMission mission) {
        try {
            for (RobotMission.MissionStep step : mission.getSteps()) {
                RobotCommand command = new RobotCommand();
                command.setRobotId(mission.getRobotId());
                command.setOrderId(mission.getOrderId());
                command.setType(CommandType.valueOf(step.getAction()));
                command.setTargetLocation(step.getTargetPositionString());
                commandPort.publishCommand(command);
            }
        } catch (Exception e) {
            log.error("[MissionOptimizer] Failed to publish mission: {}", e.getMessage());
        }
    }
}
"""

for rel_path, content in files.items():
    full_path = os.path.join(base, rel_path.replace("/", os.sep))
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"Created: {rel_path}")

print("Done!")