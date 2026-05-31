package com.smartlogistics.analytics.domain.model;

import java.time.Instant;

public class CongestionSample {

    private final String zoneId;
    private final int robotCount;
    private final double averageWaitTime;
    private final Instant timestamp;

    public CongestionSample(String zoneId, int robotCount, double averageWaitTime, Instant timestamp) {
        this.zoneId = zoneId;
        this.robotCount = robotCount;
        this.averageWaitTime = averageWaitTime;
        this.timestamp = timestamp;
    }

    public String getZoneId() { return zoneId; }
    public int getRobotCount() { return robotCount; }
    public double getAverageWaitTime() { return averageWaitTime; }
    public Instant getTimestamp() { return timestamp; }
}
