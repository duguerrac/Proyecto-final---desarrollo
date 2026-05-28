package com.smartlogistics.robotstatus.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a command sent to a robot.
 * Pure Java — zero framework imports.
 */
public class RobotCommand {

    private String robotId;
    private CommandType type;
    private String targetLocation;
    private List<String> routePoints;
    private String itemSku;
    private String orderId;

    public RobotCommand() {
        this.routePoints = new ArrayList<>();
    }

    public RobotCommand(String robotId, CommandType type, String targetLocation,
                        List<String> routePoints, String itemSku, String orderId) {
        this.robotId = robotId;
        this.type = type;
        this.targetLocation = targetLocation;
        this.routePoints = routePoints != null ? new ArrayList<>(routePoints) : new ArrayList<>();
        this.itemSku = itemSku;
        this.orderId = orderId;
    }

    /**
     * Domain rule: a command is valid if it has a robotId and a type.
     */
    public boolean isValid() {
        return robotId != null && !robotId.isBlank() && type != null;
    }

    // --- Getters and Setters ---

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public CommandType getType() {
        return type;
    }

    public void setType(CommandType type) {
        this.type = type;
    }

    public String getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(String targetLocation) {
        this.targetLocation = targetLocation;
    }

    public List<String> getRoutePoints() {
        return routePoints;
    }

    public void setRoutePoints(List<String> routePoints) {
        this.routePoints = routePoints != null ? new ArrayList<>(routePoints) : new ArrayList<>();
    }

    public String getItemSku() {
        return itemSku;
    }

    public void setItemSku(String itemSku) {
        this.itemSku = itemSku;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RobotCommand that = (RobotCommand) o;
        return Objects.equals(robotId, that.robotId)
                && type == that.type
                && Objects.equals(targetLocation, that.targetLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(robotId, type, targetLocation);
    }

    @Override
    public String toString() {
        return "RobotCommand{" +
                "robotId='" + robotId + '\'' +
                ", type=" + type +
                ", targetLocation='" + targetLocation + '\'' +
                ", routePoints=" + routePoints +
                ", itemSku='" + itemSku + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}