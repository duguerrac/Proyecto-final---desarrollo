package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "route_edge", uniqueConstraints = @UniqueConstraint(name = "uk_route_edge_source_target", columnNames = {"source_id", "target_id"}))
public class RouteEdgeJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal distance;

    @Column(nullable = false)
    private boolean bidirectional;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal weight;

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public BigDecimal getDistance() { return distance; }
    public void setDistance(BigDecimal distance) { this.distance = distance; }
    public boolean isBidirectional() { return bidirectional; }
    public void setBidirectional(boolean bidirectional) { this.bidirectional = bidirectional; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
}
