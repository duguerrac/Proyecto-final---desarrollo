package com.smartlogistics.robotstatus.domain.model;

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

    // --- Mutators ---

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }
    public void setOperationalMode(String mode) { this.operationalMode = mode; }
    public void setOperationalMode(RobotStatus status) { this.operationalMode = status.name(); }

    // --- Business logic ---

    /**
     * A robot is assignable when it is available (IDLE) and has battery > 10%.
     */
    public boolean isAssignable() {
        return available && batteryLevel > 10;
    }

    /**
     * Update telemetry data from the simulation.
     */
    public void updateTelemetry(int batteryLevel, String currentLocation, String operationalMode) {
        this.batteryLevel = batteryLevel;
        // Only update location if the telemetry provides a non-empty value
        if (currentLocation != null && !currentLocation.isEmpty()) {
            this.currentLocation = currentLocation;
        }
        if (operationalMode != null && !operationalMode.isEmpty()) {
            this.operationalMode = operationalMode;
        }
    }

    @Override
    public String toString() {
        return "Robot{id='" + id + "', mode=" + operationalMode +
               ", battery=" + batteryLevel + "%, available=" + available +
               ", loc='" + currentLocation + "'}";
    }
}