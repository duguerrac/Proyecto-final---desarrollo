package com.smartlogistics.analytics.infrastructure.adapter.out.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "route_events")
public class RouteEventDocument {

    @Id
    private String eventId;
    private String orderId;
    private String robotId;
    private List<String> path;
    private double distance;
    private long duration;
    private Instant timestamp;

    public RouteEventDocument() {}

    public RouteEventDocument(String eventId, String orderId, String robotId, List<String> path, double distance, long duration, Instant timestamp) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.robotId = robotId;
        this.path = path;
        this.distance = distance;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public List<String> getPath() { return path; }
    public void setPath(List<String> path) { this.path = path; }
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
