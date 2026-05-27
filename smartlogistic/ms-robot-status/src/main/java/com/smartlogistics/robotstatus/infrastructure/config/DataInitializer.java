package com.smartlogistics.robotstatus.infrastructure.config;

import com.smartlogistics.robotstatus.application.port.in.UpdateBatteryUseCase;
import com.smartlogistics.robotstatus.application.service.RobotStatusService;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds 5 robots into Redis at application startup.
 * 2 robots with battery < 15% (RBT-LOW, RBT-03)
 * 3 robots with battery >= 15% (RBT-01, RBT-02, RBT-04)
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UpdateBatteryUseCase updateBatteryUseCase;
    private final RobotStatusService robotStatusService;

    public DataInitializer(UpdateBatteryUseCase updateBatteryUseCase,
                           RobotStatusService robotStatusService) {
        this.updateBatteryUseCase = updateBatteryUseCase;
        this.robotStatusService = robotStatusService;
    }

    @Override
    public void run(String... args) {
        seedRobot("RBT-01", "Robot Alpha", 85, true, "DOCK-01", RobotStatus.IDLE);
        seedRobot("RBT-02", "Robot Beta", 72, true, "AISLE-A-03", RobotStatus.IDLE);
        seedRobot("RBT-03", "Robot Gamma", 8, true, "CHARGE-STATION", RobotStatus.CHARGING);
        seedRobot("RBT-04", "Robot Delta", 50, true, "DOCK-02", RobotStatus.IDLE);
        seedRobot("RBT-LOW", "Robot Low Battery", 10, true, "AISLE-B-01", RobotStatus.IDLE);

        // Publish initial batch snapshot for Unreal Engine simulation
        robotStatusService.publishBatchSnapshot();

        System.out.println("[DataInitializer] Seeded 5 robots into Redis + NATS batch snapshot published");
    }

    private void seedRobot(String id, String name, int battery, boolean available,
                           String location, RobotStatus mode) {
        Robot robot = new Robot(id, name, battery, available, location, mode);
        updateBatteryUseCase.saveRobot(robot);
    }
}