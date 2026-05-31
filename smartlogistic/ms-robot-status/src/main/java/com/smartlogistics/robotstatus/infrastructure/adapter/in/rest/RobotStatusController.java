package com.smartlogistics.robotstatus.infrastructure.adapter.in.rest;

import com.smartlogistics.robotstatus.application.port.in.GetRobotStatusUseCase;
import com.smartlogistics.robotstatus.application.port.in.RegisterRobotUseCase;
import com.smartlogistics.robotstatus.domain.exception.RobotNotFoundException;
import com.smartlogistics.robotstatus.domain.model.Robot;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/robots")
public class RobotStatusController {

    private final GetRobotStatusUseCase getRobotStatus;
    private final RegisterRobotUseCase registerRobot;

    public RobotStatusController(GetRobotStatusUseCase getRobotStatus,
                                 RegisterRobotUseCase registerRobot) {
        this.getRobotStatus = getRobotStatus;
        this.registerRobot = registerRobot;
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<RobotStatusResponse> getStatus(@PathVariable String id) {
        Robot robot = getRobotStatus.getStatus(id);
        return ResponseEntity.ok(RobotStatusResponse.from(robot));
    }

    @PostMapping
    public ResponseEntity<RobotStatusResponse> register(@RequestBody RegisterRobotRequest request) {
        Robot robot = new Robot(
                request.robotId(),
                request.name(),
                request.batteryLevel(),
                request.available(),
                request.currentLocation(),
                request.operationalMode()
        );
        Robot saved = registerRobot.register(robot);
        return ResponseEntity.status(HttpStatus.CREATED).body(RobotStatusResponse.from(saved));
    }

    @ExceptionHandler(RobotNotFoundException.class)
    public ResponseEntity<String> handleNotFound(RobotNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    public record RobotStatusResponse(String robotId, String name, int batteryLevel,
                                      boolean available, String currentLocation,
                                      String operationalMode) {
        static RobotStatusResponse from(Robot robot) {
            return new RobotStatusResponse(
                    robot.id(), robot.name(), robot.batteryLevel(),
                    robot.available(), robot.currentLocation(), robot.operationalMode()
            );
        }
    }

    public record RegisterRobotRequest(String robotId, String name, int batteryLevel,
                                       boolean available, String currentLocation,
                                       String operationalMode) {}
}
