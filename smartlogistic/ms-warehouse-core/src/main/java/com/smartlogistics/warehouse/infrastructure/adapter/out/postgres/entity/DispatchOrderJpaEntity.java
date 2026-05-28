package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dispatch_order")
public class DispatchOrderJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "assigned_robot_id", length = 50)
    private String assignedRobotId;

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPriority() { return priority; }
    public String getAssignedRobotId() { return assignedRobotId; }
    public void setStatus(String status) { this.status = status; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setAssignedRobotId(String assignedRobotId) { this.assignedRobotId = assignedRobotId; }
}
