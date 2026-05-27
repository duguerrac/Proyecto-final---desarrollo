package com.smartlogistics.robotstatus.domain.model;

import java.util.Objects;

/**
 * Domain entity representing an autonomous warehouse robot.
 * Pure Java — zero framework imports.
 */
public class Robot {

    private String id;
    private String name;
    private int batteryLevel;
    private boolean available;
    private String currentLocation;
    private RobotStatus operationalMode;

    public Robot() {
    }

    public Robot(String id, String name, int batteryLevel, boolean available,
                 String currentLocation, RobotStatus operationalMode) {
        this.id = id;
        this.name = name;
        this.batteryLevel = batteryLevel;
        this.available = available;
        this.currentLocation = currentLocation;
        this.operationalMode = operationalMode;
    }

    /**
     * Domain rule: a robot is assignable only if battery >= 15% and is available.
     */
    public boolean isAssignable() {
        return available && batteryLevel >= 15;
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public RobotStatus getOperationalMode() {
        return operationalMode;
    }

    public void setOperationalMode(RobotStatus operationalMode) {
        this.operationalMode = operationalMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Robot robot = (Robot) o;
        return Objects.equals(id, robot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Robot{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", batteryLevel=" + batteryLevel +
                ", available=" + available +
                ", currentLocation='" + currentLocation + '\'' +
                ", operationalMode=" + operationalMode +
                '}';
    }
}