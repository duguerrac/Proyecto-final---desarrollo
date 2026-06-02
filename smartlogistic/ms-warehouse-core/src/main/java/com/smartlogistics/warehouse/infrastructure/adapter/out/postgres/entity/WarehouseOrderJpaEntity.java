package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "warehouse_order")
public class WarehouseOrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "pickup_spot_code", length = 30)
    private String pickupSpotCode;

    @Column(name = "delivery_point", length = 30)
    private String deliveryPoint;

    @Column(name = "robot_id", length = 50)
    private String robotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getPickupSpotCode() { return pickupSpotCode; }
    public void setPickupSpotCode(String pickupSpotCode) { this.pickupSpotCode = pickupSpotCode; }
    public String getDeliveryPoint() { return deliveryPoint; }
    public void setDeliveryPoint(String deliveryPoint) { this.deliveryPoint = deliveryPoint; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderLineJpaEntity> getLines() { return lines; }
    public void setLines(List<OrderLineJpaEntity> lines) { this.lines = lines; }
}