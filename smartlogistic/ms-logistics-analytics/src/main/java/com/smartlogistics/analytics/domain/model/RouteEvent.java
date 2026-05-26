package com.smartlogistics.analytics.domain.model;

import java.time.Instant;
import java.util.List;

public class RouteEvent {

    private final String eventId;
    private final String orderId;
    private final String robotId;
    private final List<String> path;
    private final double distance;
    private final long duration;
    private final Instant timestamp;

    public RouteEvent(String eventId, String orderId, String robotId, List<String> path, double distance, long duration, Instant timestamp) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.robotId = robotId;
        this.path = path != null ? List.copyOf(path) : List.of();
        this.distance = distance;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public String getOrderId() { return orderId; }
    public String getRobotId() { return robotId; }
    public List<String> getPath() { return path; }
    public double getDistance() { return distance; }
    public long getDuration() { return duration; }
    public Instant getTimestamp() { return timestamp; }
}
