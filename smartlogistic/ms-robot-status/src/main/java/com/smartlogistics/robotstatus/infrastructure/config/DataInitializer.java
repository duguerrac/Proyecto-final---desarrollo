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
        cache.save(new Robot("RBT-01", "Alpha", 85, true, "RP-START", "AUTONOMOUS"));
        cache.save(new Robot("RBT-02", "Beta", 72, true, "RP-A1-02", "AUTONOMOUS"));
        cache.save(new Robot("RBT-03", "Gamma", 45, true, "RP-B1-01", "AUTONOMOUS"));
        cache.save(new Robot("RBT-LOW", "Delta", 10, true, "RP-CHARGE", "CHARGING"));
        cache.save(new Robot("RBT-MID", "Epsilon", 12, false, "RP-EXIT", "MAINTENANCE"));
    }
}
