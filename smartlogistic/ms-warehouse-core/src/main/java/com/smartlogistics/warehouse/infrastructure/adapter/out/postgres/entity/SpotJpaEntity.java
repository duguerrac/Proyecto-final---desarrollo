package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "spot")
public class SpotJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String aisle;

    @Column(nullable = false, length = 20)
    private String section;

    @Column(nullable = false)
    private int level;

    @Column(name = "root_point_id")
    private Long rootPointId;

    @Column(precision = 8, scale = 2)
    private BigDecimal x;

    @Column(precision = 8, scale = 2)
    private BigDecimal y;

    @Column(precision = 8, scale = 2)
    private BigDecimal z;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getAisle() { return aisle; }
    public void setAisle(String aisle) { this.aisle = aisle; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public Long getRootPointId() { return rootPointId; }
    public void setRootPointId(Long rootPointId) { this.rootPointId = rootPointId; }
    public BigDecimal getX() { return x; }
    public void setX(BigDecimal x) { this.x = x; }
    public BigDecimal getY() { return y; }
    public void setY(BigDecimal y) { this.y = y; }
    public BigDecimal getZ() { return z; }
    public void setZ(BigDecimal z) { this.z = z; }
}
