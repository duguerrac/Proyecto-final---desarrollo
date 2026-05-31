package com.smartlogistics.robotstatus.application.port.out;

import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;
import java.util.Optional;

public interface RobotCachePort {
    void save(Robot robot);
    Optional<Robot> findById(String robotId);
    List<Robot> findAll();
}
