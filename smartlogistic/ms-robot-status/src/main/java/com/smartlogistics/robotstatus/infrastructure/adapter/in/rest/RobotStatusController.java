package com.smartlogistics.robotstatus.infrastructure.adapter.in.rest;

import com.smartlogistics.robotstatus.application.port.in.DispatchRobotUseCase;
import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.RegisterRobotUseCase;
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

@RestController
@RequestMapping("/api/robots")
public class RobotStatusController {

    private static final Logger log = LoggerFactory.getLogger(RobotStatusController.class);

    private final GetRobotStatusUseCase getRobotStatus;
    private final RegisterRobotUseCase registerRobot;
    private final DispatchRobotUseCase dispatchRobot;
    private final SseTelemetryAdapter sseTelemetryAdapter;
    private final ObjectMapper objectMapper;

    public RobotStatusController(GetRobotStatusUseCase getRobotStatus,
                                 RegisterRobotUseCase registerRobot,
                                 DispatchRobotUseCase dispatchRobot,
                                 SseTelemetryAdapter sseTelemetryAdapter,
                                 ObjectMapper objectMapper) {
        this.getRobotStatus = getRobotStatus;
        this.registerRobot = registerRobot;
        this.dispatchRobot = dispatchRobot;
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
        Robot saved = registerRobot.register(robot); // save updates via cache

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
                                String operationalMode) {
        static RobotResponse from(Robot robot) {
            return new RobotResponse(
                    robot.getId(), robot.getName(), robot.getBatteryLevel(),
                    robot.isAvailable(), robot.getCurrentLocation(),
                    robot.getOperationalMode()
            );
        }
    }

    public record RegisterRobotRequest(String robotId, String name, int batteryLevel,
                                       boolean available, String currentLocation,
                                       String operationalMode) {}

    public record TelemetryRequest(int batteryLevel, String currentLocation,
                                   String operationalMode) {}
}