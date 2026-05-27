package com.smartlogistics.robotstatus.infrastructure.config;

import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.application.port.out.RobotEventPort;
import com.smartlogistics.robotstatus.application.service.RobotStatusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public RobotStatusService robotStatusService(RobotCachePort robotCachePort,
                                                  RobotEventPort robotEventPort) {
        return new RobotStatusService(robotCachePort, robotEventPort);
    }
}
