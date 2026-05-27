package com.smartlogistics.robotstatus.infrastructure.adapter.in.rest;

import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.UpdateBatteryUseCase;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.Robot;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/robots")
public class RobotStatusController {

    private final GetRobotStatusUseCase getRobotStatusUseCase;
    private final UpdateBatteryUseCase updateBatteryUseCase;

    public RobotStatusController(GetRobotStatusUseCase getRobotStatusUseCase,
                                 UpdateBatteryUseCase updateBatteryUseCase) {
        this.getRobotStatusUseCase = getRobotStatusUseCase;
        this.updateBatteryUseCase = updateBatteryUseCase;
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getRobotStatus(@PathVariable String id) {
        Robot robot = getRobotStatusUseCase.getRobotStatus(id)
                .orElseThrow(() -> new RobotNotFoundException(id));

        return ResponseEntity.ok(Map.of(
                "id", robot.getId(),
                "name", robot.getName() != null ? robot.getName() : "",
                "batteryLevel", robot.getBatteryLevel(),
                "available", robot.isAvailable(),
                "currentLocation", robot.getCurrentLocation() != null ? robot.getCurrentLocation() : "",
                "operationalMode", robot.getOperationalMode() != null ? robot.getOperationalMode().name() : "UNKNOWN",
                "assignable", robot.isAssignable()
        ));
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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "batteryLevel", saved.getBatteryLevel(),
                "available", saved.isAvailable(),
                "currentLocation", saved.getCurrentLocation(),
                "operationalMode", saved.getOperationalMode().name(),
                "assignable", saved.isAssignable()
        ));
    }

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