package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "route_plan")
public class RoutePlanJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "robot_id", length = 50)
    private String robotId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "total_distance", precision = 10, scale = 2)
    private BigDecimal totalDistance;

    @Column(name = "speed_factor", nullable = false, precision = 5, scale = 2)
    private BigDecimal speedFactor = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getRobotId() { return robotId; }
    public String getStatus() { return status; }
    public BigDecimal getTotalDistance() { return totalDistance; }
    public BigDecimal getSpeedFactor() { return speedFactor; }
    public Instant getCreatedAt() { return createdAt; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public void setStatus(String status) { this.status = status; }
    public void setTotalDistance(BigDecimal totalDistance) { this.totalDistance = totalDistance; }
    public void setSpeedFactor(BigDecimal speedFactor) { this.speedFactor = speedFactor; }
}
