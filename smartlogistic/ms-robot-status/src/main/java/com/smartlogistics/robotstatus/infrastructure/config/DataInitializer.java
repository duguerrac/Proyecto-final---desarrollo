package com.smartlogistics.robotstatus.infrastructure.config;

import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final RobotCachePort cache;

    public DataInitializer(RobotCachePort cache) {
        this.cache = cache;
    }

    @Override
    public void run(String... args) {
        // Locations must match V8 grid layout root_point codes
        // RobotStatus enum: IDLE, MOVING, LOADING, UNLOADING, CHARGING, ERROR, DISPATCHED
        cache.save(new Robot("RBT-01", "Alpha",   85, true,  "RP-R00-C00", "IDLE"));       // START/entry
        cache.save(new Robot("RBT-02", "Beta",    72, true,  "RP-R01-C05", "IDLE"));       // Main corridor
        cache.save(new Robot("RBT-03", "Gamma",   45, true,  "RP-R03-C05", "IDLE"));       // Middle corridor
        cache.save(new Robot("RBT-LOW", "Delta",  10, true,  "RP-R04-C00", "CHARGING"));   // Charging station
        cache.save(new Robot("RBT-MID", "Epsilon", 12, false, "RP-R05-C09", "ERROR"));     // Charging station (maintenance)
    }
}
