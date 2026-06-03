package com.smartlogistics.robotstatus.infrastructure.adapter.in.rest;

import com.smartlogistics.robotstatus.application.port.in.DispatchRobotUseCase;
import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.RegisterRobotUseCase;
import com.smartlogistics.robotstatus.application.port.out.RobotDispatchPort;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.infrastructure.adapter.out.sse.SseTelemetryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/robots")
public class RobotStatusController {

    private static final Logger log = LoggerFactory.getLogger(RobotStatusController.class);

    private final GetRobotStatusUseCase getRobotStatus;
    private final RegisterRobotUseCase registerRobot;
    private final DispatchRobotUseCase dispatchRobot;
    private final RobotDispatchPort robotDispatchPort;
    private final SseTelemetryAdapter sseTelemetryAdapter;
    private final ObjectMapper objectMapper;

    public RobotStatusController(GetRobotStatusUseCase getRobotStatus,
                                 RegisterRobotUseCase registerRobot,
                                 DispatchRobotUseCase dispatchRobot,
                                 RobotDispatchPort robotDispatchPort,
                                 SseTelemetryAdapter sseTelemetryAdapter,
                                 ObjectMapper objectMapper) {
        this.getRobotStatus = getRobotStatus;
        this.registerRobot = registerRobot;
        this.dispatchRobot = dispatchRobot;
        this.robotDispatchPort = robotDispatchPort;
        this.sseTelemetryAdapter = sseTelemetryAdapter;
        this.objectMapper = objectMapper;
    }

    // ── Simulation → Backend: Register a new robot ──────────────────────

    @PostMapping
    public ResponseEntity<RobotResponse> register(@RequestBody RegisterRobotRequest request) {
        log.info("Registering robot: {}", request.robotId);
        Robot robot = new Robot(
                request.robotId,
                request.name,
                request.batteryLevel,
                request.available,
                request.currentLocation,
                request.operationalMode
        );
        Robot saved = registerRobot.register(robot);
        return ResponseEntity.status(HttpStatus.CREATED).body(RobotResponse.from(saved));
    }

    // ── Simulation → Backend: Send telemetry updates ────────────────────

    @PutMapping("/{id}/telemetry")
    public ResponseEntity<RobotResponse> updateTelemetry(
            @PathVariable String id,
            @RequestBody TelemetryRequest request) {
        log.debug("Telemetry update from robot {}: battery={}%, location={}, mode={}",
                id, request.batteryLevel, request.currentLocation, request.operationalMode);
        Robot robot = getRobotStatus.getStatus(id);
        robot.updateTelemetry(
                request.batteryLevel,
                request.currentLocation,
                request.operationalMode
        );
        Robot saved = getRobotStatus.saveRobot(robot); // save + publish status event

        // Broadcast real-time SSE event to all connected clients
        try {
            String jsonPayload = objectMapper.writeValueAsString(
                    java.util.Map.of("robot", java.util.Map.of(
                            "id", saved.getId(),
                            "name", saved.getName() != null ? saved.getName() : "",
                            "batteryLevel", saved.getBatteryLevel(),
                            "available", saved.isAvailable(),
                            "currentLocation", saved.getCurrentLocation() != null ? saved.getCurrentLocation() : "",
                            "operationalMode", saved.getOperationalMode() != null ? saved.getOperationalMode() : "IDLE"
                    ))
            );
            sseTelemetryAdapter.broadcastTelemetry(saved.getId(), jsonPayload);
        } catch (Exception e) {
            log.warn("[SSE] Failed to broadcast telemetry update: {}", e.getMessage());
        }

        return ResponseEntity.ok(RobotResponse.from(saved));
    }

    // ── Simulation → Backend: Robot completed its route ─────────────────

    @PostMapping("/{id}/route-complete")
    public ResponseEntity<Void> routeComplete(@PathVariable String id) {
        log.info("Robot {} completed route, marking available", id);
        dispatchRobot.markRobotAvailable(id);
        return ResponseEntity.ok().build();
    }

    // ── Simulation → Backend: Mission events (package picked/delivered) ─

    @PostMapping("/events")
    public ResponseEntity<Void> receiveEvent(@RequestBody Map<String, Object> eventBody) {
        String eventType = (String) eventBody.get("eventType");
        if (eventType == null) {
            log.warn("[Events] Received event without eventType");
            return ResponseEntity.badRequest().build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) eventBody.get("payload");
        if (payload == null) {
            payload = eventBody; // flat structure support
        }

        log.info("[Events] Received event: type={}, robotId={}, packageId={}",
                eventType, payload.get("robotId"), payload.get("packageId"));

        switch (eventType) {
            case "MISSION_COMPLETED" -> {
                String robotId = payload.get("robotId") != null ? payload.get("robotId").toString() : "";
                Object pkgIdObj = payload.get("packageId");
                String packageId = pkgIdObj != null ? pkgIdObj.toString() : "";
                String missionType = payload.get("missionType") != null ? payload.get("missionType").toString() : "";
                String spotCode = payload.get("spotCode") != null ? payload.get("spotCode").toString() : "";

                if ("STOCK_IN".equals(missionType) || "STOCK_OUT".equals(missionType)) {
                    // Package was delivered — publish package.delivered with mission context
                    robotDispatchPort.publishPackageDelivered(packageId, missionType, spotCode);
                    dispatchRobot.markRobotAvailable(robotId);
                    log.info("[Events] Package #{} DELIVERED by robot {} (missionType={}) — marking available", packageId, robotId, missionType);
                }
            }
            case "PACKAGE_PICKED" -> {
                String robotId = payload.get("robotId") != null ? payload.get("robotId").toString() : "";
                String packageId = payload.get("packageId") != null ? payload.get("packageId").toString() : "";
                robotDispatchPort.publishPackageTaken(packageId, robotId);
                log.info("[Events] Package #{} PICKED by robot {}", packageId, robotId);
            }
            case "PACKAGE_DELIVERED" -> {
                String robotId = payload.get("robotId") != null ? payload.get("robotId").toString() : "";
                String packageId = payload.get("packageId") != null ? payload.get("packageId").toString() : "";
                String missionType = payload.get("missionType") != null ? payload.get("missionType").toString() : "";
                String spotCode = payload.get("spotCode") != null ? payload.get("spotCode").toString() : "";
                robotDispatchPort.publishPackageDelivered(packageId, missionType, spotCode);
                dispatchRobot.markRobotAvailable(robotId);
                log.info("[Events] Package #{} DELIVERED by robot {} (missionType={}) — marking available", packageId, robotId, missionType);
            }
            default -> log.debug("[Events] Unhandled event type: {}", eventType);
        }

        return ResponseEntity.ok().build();
    }

    // ── Query endpoints ─────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<RobotResponse>> listRobots() {
        List<Robot> robots = getRobotStatus.getAllRobots();
        List<RobotResponse> response = robots.stream()
                .map(RobotResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<RobotResponse> getStatus(@PathVariable String id) {
        Robot robot = getRobotStatus.getStatus(id);
        return ResponseEntity.ok(RobotResponse.from(robot));
    }

    @GetMapping("/available")
    public ResponseEntity<List<RobotResponse>> getAvailableRobots() {
        List<Robot> robots = getRobotStatus.getAvailableRobots();
        List<RobotResponse> response = robots.stream()
                .map(RobotResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    // ── HTTP Fallback: UE5 polls for pending missions ────────────────────

    /**
     * Returns all robots that have a pending mission.
     * UE5 can poll this endpoint as a fallback when STOMP is not connected.
     * This is READ-ONLY — does NOT clear the mission (prevents race condition
     * where clearing pendingMission allows telemetry to overwrite MOVING→IDLE).
     * Use DELETE /api/robots/{id}/pending-mission to clear after processing.
     */
    @GetMapping("/pending-missions")
    public ResponseEntity<List<java.util.Map<String, Object>>> getPendingMissions() {
        List<java.util.Map<String, Object>> missions = new java.util.ArrayList<>();
        for (Robot robot : getRobotStatus.getAllRobots()) {
            if (robot.getPendingMission() != null) {
                java.util.Map<String, Object> entry = new java.util.HashMap<>();
                entry.put("robotId", robot.getId());
                entry.put("mission", robot.getPendingMission());
                missions.add(entry);
                log.debug("[HTTP-Mission] Robot {} has pending mission (read-only, not cleared)", robot.getId());
            }
        }
        return ResponseEntity.ok(missions);
    }

    /**
     * Clear a robot's pending mission after UE5 has confirmed processing.
     * This prevents the race condition where clearing the mission too early
     * allows telemetry to overwrite MOVING→IDLE.
     */
    @DeleteMapping("/{id}/pending-mission")
    public ResponseEntity<Void> clearPendingMission(@PathVariable String id) {
        Robot robot = getRobotStatus.getStatus(id);
        if (robot.getPendingMission() != null) {
            robot.setPendingMission(null);
            getRobotStatus.saveRobot(robot);
            log.info("[HTTP-Mission] Robot {} pending mission cleared after confirmation", id);
        }
        return ResponseEntity.ok().build();
    }

    // ── SSE Telemetry Stream ────────────────────────────────────────────

    @GetMapping(value = "/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry() {
        log.info("[SSE] New telemetry stream client connected");
        return sseTelemetryAdapter.createEmitter();
    }

    // ── Error handling ──────────────────────────────────────────────────

    @ExceptionHandler(RobotNotFoundException.class)
    public ResponseEntity<String> handleNotFound(RobotNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    // ── DTOs ────────────────────────────────────────────────────────────

    public record RobotResponse(String robotId, String name, int batteryLevel,
                                boolean available, String currentLocation,
                                String operationalMode,
                                java.util.Map<String, Object> pendingMission) {
        static RobotResponse from(Robot robot) {
            return new RobotResponse(
                    robot.getId(), robot.getName(), robot.getBatteryLevel(),
                    robot.isAvailable(), robot.getCurrentLocation(),
                    robot.getOperationalMode(),
                    robot.getPendingMission()
            );
        }
    }

    public record RegisterRobotRequest(String robotId, String name, int batteryLevel,
                                       boolean available, String currentLocation,
                                       String operationalMode) {}

    public record TelemetryRequest(int batteryLevel, String currentLocation,
                                   String operationalMode) {}
}