package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.time.Instant;

public class PackageResponse {
    private Long id;
    private String sku;
    private int quantity;
    private String status;
    private String receptionSpotCode;
    private String targetSpotCode;
    private String robotId;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReceptionSpotCode() { return receptionSpotCode; }
    public void setReceptionSpotCode(String code) { this.receptionSpotCode = code; }
    public String getTargetSpotCode() { return targetSpotCode; }
    public void setTargetSpotCode(String code) { this.targetSpotCode = code; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}