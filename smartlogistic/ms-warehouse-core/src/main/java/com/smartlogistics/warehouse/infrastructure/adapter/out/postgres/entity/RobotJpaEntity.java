package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "robot")
public class RobotJpaEntity {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "battery_level", nullable = false)
    private int batteryLevel = 100;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "current_location", length = 30)
    private String currentLocation;

    @Column(name = "operational_mode", nullable = false, length = 20)
    private String operationalMode = "IDLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public String getOperationalMode() { return operationalMode; }
    public void setOperationalMode(String operationalMode) { this.operationalMode = operationalMode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}