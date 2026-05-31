package com.smartlogistics.robotstatus.domain.model;

public class Robot {

    private final String id;
    private final String name;
    private int batteryLevel;
    private boolean available;
    private String currentLocation;
    private String operationalMode;

    public Robot(String id, String name, int batteryLevel, boolean available,
                 String currentLocation, String operationalMode) {
        this.id = id;
        this.name = name;
        this.batteryLevel = batteryLevel;
        this.available = available;
        this.currentLocation = currentLocation;
        this.operationalMode = operationalMode;
    }

    public String id() { return id; }
    public String name() { return name; }
    public int batteryLevel() { return batteryLevel; }
    public boolean available() { return available; }
    public String currentLocation() { return currentLocation; }
    public String operationalMode() { return operationalMode; }

    public void updateStatus(int batteryLevel, boolean available,
                             String currentLocation, String operationalMode) {
        this.batteryLevel = batteryLevel;
        this.available = available;
        this.currentLocation = currentLocation;
        this.operationalMode = operationalMode;
    }
}
