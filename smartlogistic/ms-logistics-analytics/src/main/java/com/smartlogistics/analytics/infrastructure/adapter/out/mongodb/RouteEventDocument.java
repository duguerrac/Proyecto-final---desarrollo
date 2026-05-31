package com.smartlogistics.analytics.infrastructure.adapter.out.mongodb;

import com.smartlogistics.analytics.domain.model.PathPoint;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "route_events")
public class RouteEventDocument {

    @Id
    private String eventId;
    private String eventType;
    private String orderId;
    private String robotId;
    private String warehouseId;
    private boolean fragileItems;
    private List<PathPoint> path;
    private double distance;
    private long duration;
    private Instant timestamp;

    public RouteEventDocument() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getWarehouseId() { return warehouseId; }
    public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
    public boolean isFragileItems() { return fragileItems; }
    public void setFragileItems(boolean fragileItems) { this.fragileItems = fragileItems; }
    public List<PathPoint> getPath() { return path; }
    public void setPath(List<PathPoint> path) { this.path = path; }
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
