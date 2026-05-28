package com.smartlogistics.robotstatus.infrastructure.adapter.in.rest;

import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.SendRobotCommandUseCase;
import com.smartlogistics.robotstatus.application.port.in.UpdateBatteryUseCase;
import com.smartlogistics.robotstatus.application.port.out.TelemetryStreamPort;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.CommandType;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/robots")
public class RobotStatusController {

    private final GetRobotStatusUseCase getRobotStatusUseCase;
    private final UpdateBatteryUseCase updateBatteryUseCase;
    private final SendRobotCommandUseCase sendRobotCommandUseCase;
    private final TelemetryStreamPort telemetryStreamPort;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public RobotStatusController(GetRobotStatusUseCase getRobotStatusUseCase,
                                 UpdateBatteryUseCase updateBatteryUseCase,
                                 SendRobotCommandUseCase sendRobotCommandUseCase,
                                 TelemetryStreamPort telemetryStreamPort) {
        this.getRobotStatusUseCase = getRobotStatusUseCase;
        this.updateBatteryUseCase = updateBatteryUseCase;
        this.sendRobotCommandUseCase = sendRobotCommandUseCase;
        this.telemetryStreamPort = telemetryStreamPort;
    }

    // ──────────── Status Endpoints ────────────

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllRobots() {
        List<Map<String, Object>> robots = getRobotStatusUseCase.getAllRobots().stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(robots);
    }

    @GetMapping("/available")
    public ResponseEntity<List<Map<String, Object>>> getAvailableRobots() {
        List<Map<String, Object>> robots = getRobotStatusUseCase.getAvailableRobots().stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(robots);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getRobotStatus(@PathVariable String id) {
        Robot robot = getRobotStatusUseCase.getRobotStatus(id)
                .orElseThrow(() -> new RobotNotFoundException(id));
        return ResponseEntity.ok(toMap(robot));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrUpdateRobot(@RequestBody Map<String, Object> body) {
        Robot robot = new Robot();
        robot.setId((String) body.get("id"));
        robot.setName((String) body.getOrDefault("name", body.get("id")));
        robot.setBatteryLevel(body.containsKey("batteryLevel") ? ((Number) body.get("batteryLevel")).intValue() : 100);
        robot.setAvailable(body.containsKey("available") ? (Boolean) body.get("available") : true);
        robot.setCurrentLocation((String) body.getOrDefault("currentLocation", "DOCK-01"));

        String mode = (String) body.getOrDefault("operationalMode", "IDLE");
        robot.setOperationalMode(com.smartlogistics.robotstatus.domain.model.RobotStatus.valueOf(mode));

        Robot saved = updateBatteryUseCase.saveRobot(robot);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateOperationalMode(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        Robot robot = getRobotStatusUseCase.getRobotStatus(id)
                .orElseThrow(() -> new RobotNotFoundException(id));

        if (body.containsKey("operationalMode")) {
            String mode = (String) body.get("operationalMode");
            robot.setOperationalMode(com.smartlogistics.robotstatus.domain.model.RobotStatus.valueOf(mode));
        }
        if (body.containsKey("batteryLevel")) {
            robot.setBatteryLevel(((Number) body.get("batteryLevel")).intValue());
        }
        if (body.containsKey("available")) {
            robot.setAvailable((Boolean) body.get("available"));
        }
        if (body.containsKey("currentLocation")) {
            robot.setCurrentLocation((String) body.get("currentLocation"));
        }

        Robot saved = updateBatteryUseCase.saveRobot(robot);
        return ResponseEntity.ok(toMap(saved));
    }

    // ──────────── Command Endpoint ────────────

    @PostMapping("/{id}/command")
    public ResponseEntity<Map<String, Object>> sendCommand(
            @PathVariable String id, @RequestBody Map<String, Object> body) {

        RobotCommand command = new RobotCommand();
        command.setRobotId(id);
        command.setType(CommandType.valueOf((String) body.get("type")));
        command.setTargetLocation((String) body.getOrDefault("targetLocation", null));
        command.setItemSku((String) body.getOrDefault("itemSku", null));
        command.setOrderId((String) body.getOrDefault("orderId", null));

        @SuppressWarnings("unchecked")
        List<String> routePoints = (List<String>) body.get("routePoints");
        command.setRoutePoints(routePoints);

        RobotCommand sent = sendRobotCommandUseCase.sendCommand(command);

        return ResponseEntity.ok(Map.of(
                "status", "COMMAND_SENT",
                "robotId", sent.getRobotId(),
                "commandType", sent.getType().name(),
                "targetLocation", sent.getTargetLocation() != null ? sent.getTargetLocation() : "",
                "routePoints", sent.getRoutePoints(),
                "itemSku", sent.getItemSku() != null ? sent.getItemSku() : "",
                "orderId", sent.getOrderId() != null ? sent.getOrderId() : ""
        ));
    }

    // ──────────── SSE Telemetry Stream ────────────

    @GetMapping(value = "/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry() {
        SseEmitter emitter = telemetryStreamPort.createEmitter();
        return emitter;
    }

    @GetMapping(value = "/{id}/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRobotTelemetry(@PathVariable String id) {
        SseEmitter emitter = telemetryStreamPort.createEmitterForRobot(id);
        return emitter;
    }

    // ──────────── Helpers ────────────

    private Map<String, Object> toMap(Robot robot) {
        return Map.of(
                "id", robot.getId(),
                "name", robot.getName() != null ? robot.getName() : "",
                "batteryLevel", robot.getBatteryLevel(),
                "available", robot.isAvailable(),
                "currentLocation", robot.getCurrentLocation() != null ? robot.getCurrentLocation() : "",
                "operationalMode", robot.getOperationalMode() != null ? robot.getOperationalMode().name() : "UNKNOWN",
                "assignable", robot.isAssignable()
        );
    }

    @ExceptionHandler(RobotNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(RobotNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }
}