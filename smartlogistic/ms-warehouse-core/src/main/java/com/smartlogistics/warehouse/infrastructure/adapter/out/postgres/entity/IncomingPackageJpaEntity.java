package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incoming_package")
public class IncomingPackageJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 20)
    private String status = "RECEIVED";

    @Column(name = "reception_spot_code", nullable = false, length = 50)
    private String receptionSpotCode;

    @Column(name = "target_spot_code", nullable = false, length = 50)
    private String targetSpotCode;

    @Column(name = "robot_id", length = 50)
    private String robotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
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
    public Instant getUpdatedAt() { return updatedAt; }
}