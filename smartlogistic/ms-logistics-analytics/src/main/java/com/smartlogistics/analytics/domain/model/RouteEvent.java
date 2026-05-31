package com.smartlogistics.analytics.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class RouteEvent {

    private final String eventId;
    private final String eventType;
    private final String orderId;
    private final String robotId;
    private final String warehouseId;
    private final boolean fragileItems;
    private final List<PathPoint> path;
    private final double distance;
    private final long duration;
    private final Instant timestamp;

    public RouteEvent(String eventId, String eventType, String orderId, String robotId, String warehouseId, boolean fragileItems, List<PathPoint> path, double distance, long duration, Instant timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.robotId = robotId;
        this.warehouseId = warehouseId;
        this.fragileItems = fragileItems;
        this.path = path != null ? List.copyOf(path) : List.of();
        this.distance = distance;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getOrderId() { return orderId; }
    public String getRobotId() { return robotId; }
    public String getWarehouseId() { return warehouseId; }
    public boolean isFragileItems() { return fragileItems; }
    public List<PathPoint> getPath() { return path; }
    public double getDistance() { return distance; }
    public long getDuration() { return duration; }
    public Instant getTimestamp() { return timestamp; }
}
