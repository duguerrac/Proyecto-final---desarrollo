package com.smartlogistics.robotstatus.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A mission assigned to a robot — a sequence of optimized steps
 * (GO_TO, PICK_UP, DROP_OFF, RETURN_DOCK) with full waypoint coordinates.
 */
public class RobotMission {

    private String missionId;
    private String robotId;
    private String orderId;
    private MissionStatus status;
    private int currentStepIndex;
    private List<MissionStep> steps;
    private Instant createdAt;
    private Instant updatedAt;

    public enum MissionStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    public static class MissionStep {
        private int stepIndex;
        private String action;     // GO_TO, PICK_UP, DROP_OFF, RETURN_DOCK
        private String locationName;
        private double targetX;
        private double targetY;
        private double targetZ;
        private StepStatus status;

        public enum StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

        public MissionStep() { this.status = StepStatus.PENDING; }

        public MissionStep(int stepIndex, String action, String locationName,
                           double targetX, double targetY, double targetZ) {
            this.stepIndex = stepIndex;
            this.action = action;
            this.locationName = locationName;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.status = StepStatus.PENDING;
        }

        // Position as comma-separated string for UE5
        public String getTargetPositionString() {
            return String.format("%.1f,%.1f,%.1f", targetX, targetY, targetZ);
        }

        // Getters & Setters
        public int getStepIndex() { return stepIndex; }
        public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public double getTargetX() { return targetX; }
        public void setTargetX(double targetX) { this.targetX = targetX; }
        public double getTargetY() { return targetY; }
        public void setTargetY(double targetY) { this.targetY = targetY; }
        public double getTargetZ() { return targetZ; }
        public void setTargetZ(double targetZ) { this.targetZ = targetZ; }
        public StepStatus getStatus() { return status; }
        public void setStatus(StepStatus status) { this.status = status; }
    }

    public RobotMission() {
        this.missionId = UUID.randomUUID().toString();
        this.status = MissionStatus.PENDING;
        this.currentStepIndex = 0;
        this.steps = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public MissionStep getCurrentStep() {
        if (currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    public void advanceStep() {
        if (currentStepIndex < steps.size()) {
            steps.get(currentStepIndex).setStatus(MissionStep.StepStatus.COMPLETED);
            currentStepIndex++;
            if (currentStepIndex >= steps.size()) {
                status = MissionStatus.COMPLETED;
            }
        }
        updatedAt = Instant.now();
    }

    // ─── Getters & Setters ────────────────────────────────────

    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }

    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public MissionStatus getStatus() { return status; }
    public void setStatus(MissionStatus status) { this.status = status; }

    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }

    public List<MissionStep> getSteps() { return steps; }
    public void setSteps(List<MissionStep> steps) { this.steps = steps; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}