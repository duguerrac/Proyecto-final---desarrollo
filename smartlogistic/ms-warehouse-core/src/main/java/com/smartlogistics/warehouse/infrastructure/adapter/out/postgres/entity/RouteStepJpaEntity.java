package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "route_step", uniqueConstraints = @UniqueConstraint(columnNames = {"route_plan_id", "sequence"}))
public class RouteStepJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_plan_id", nullable = false)
    private Long routePlanId;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "root_point_id", nullable = false)
    private Long rootPointId;

    @Column(nullable = false, length = 50)
    private String action;

    public Long getId() { return id; }
    public Long getRoutePlanId() { return routePlanId; }
    public int getSequence() { return sequence; }
    public Long getRootPointId() { return rootPointId; }
    public String getAction() { return action; }
    public void setRoutePlanId(Long routePlanId) { this.routePlanId = routePlanId; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public void setRootPointId(Long rootPointId) { this.rootPointId = rootPointId; }
    public void setAction(String action) { this.action = action; }
}
