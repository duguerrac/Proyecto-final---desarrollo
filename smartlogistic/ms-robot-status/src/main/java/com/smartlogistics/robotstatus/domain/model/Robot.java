package com.smartlogistics.robotstatus.domain.model;

import java.util.Map;

/**
 * Domain entity representing a warehouse robot.
 * Mutable to support status updates (telemetry, dispatch, battery).
 */
public class Robot {

    private String id;
    private String name;
    private int batteryLevel;
    private boolean available;
    private String currentLocation;
    private String operationalMode;
    private Map<String, Object> pendingMission;

    public Robot() {}

    public Robot(String id, String name, int batteryLevel, boolean available,
                 String currentLocation, String operationalMode) {
        this.id = id;
        this.name = name;
        this.batteryLevel = batteryLevel;
        this.available = available;
        this.currentLocation = currentLocation;
        this.operationalMode = operationalMode;
    }

    // --- Accessors (both styles for compatibility) ---

    public String getId() { return id; }
    public String id() { return id; }

    public String getName() { return name; }
    public String name() { return name; }

    public int getBatteryLevel() { return batteryLevel; }
    public int batteryLevel() { return batteryLevel; }

    public boolean isAvailable() { return available; }
    public boolean available() { return available; }

    public String getCurrentLocation() { return currentLocation; }
    public String currentLocation() { return currentLocation; }

    public String getOperationalMode() { return operationalMode; }
    public String operationalMode() { return operationalMode; }

    public Map<String, Object> getPendingMission() { return pendingMission; }
    public Map<String, Object> pendingMission() { return pendingMission; }

    // --- Mutators ---

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }
    public void setOperationalMode(String mode) { this.operationalMode = mode; }
    public void setOperationalMode(RobotStatus status) { this.operationalMode = status.name(); }
    public void setPendingMission(Map<String, Object> mission) { this.pendingMission = mission; }

    // --- Business logic ---

    /**
     * A robot is assignable when it is available (IDLE) and has battery > 10%.
     */
    public boolean isAssignable() {
        return available && batteryLevel > 10;
    }

    /**
     * Update telemetry data from the simulation.
     * Preserves pendingMission and operationalMode if the robot has an active mission
     * (prevents UE5 telemetry from overwriting dispatch state).
     */
    public void updateTelemetry(int batteryLevel, String currentLocation, String operationalMode) {
        this.batteryLevel = batteryLevel;
        // Only update location if the telemetry provides a non-empty value
        if (currentLocation != null && !currentLocation.isEmpty()) {
            this.currentLocation = currentLocation;
        }
        // Only update operational mode if the robot doesn't have a pending mission
        // (prevents UE5's IDLE telemetry from overwriting MOVING state during dispatch)
        if (operationalMode != null && !operationalMode.isEmpty()) {
            if (this.pendingMission == null) {
                this.operationalMode = operationalMode;
            } else if (!"IDLE".equals(operationalMode)) {
                // Allow non-IDLE modes (MOVING, PICKING, etc.) to update even with pending mission
                this.operationalMode = operationalMode;
            }
            // If pendingMission is set and telemetry says IDLE, keep the current mode
        }
        // Clear pending mission only when robot reports IDLE and has no pending mission
        // (prevents premature clearing)
        if ("IDLE".equals(operationalMode) && this.pendingMission == null) {
            // Mission already consumed or cleared — nothing to do
        }
    }

    @Override
    public String toString() {
        return "Robot{id='" + id + "', mode=" + operationalMode +
               ", battery=" + batteryLevel + "%, available=" + available +
               ", loc='" + currentLocation + "'}";
    }
}