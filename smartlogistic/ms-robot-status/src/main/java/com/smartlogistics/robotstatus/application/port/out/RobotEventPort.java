package com.smartlogistics.robotstatus.application.port.out;

import com.smartlogistics.robotstatus.domain.model.Robot;

import java.util.List;

/**
 * Output port for publishing robot status events (NATS).
 * Pure Java — zero framework imports.
 */
public interface RobotEventPort {

    /**
     * Publish a single robot status update event.
     */
    void publishStatusUpdate(Robot robot);

    /**
     * Publish a batch snapshot of all robots.
     */
    void publishStatusBatch(List<Robot> robots);
}