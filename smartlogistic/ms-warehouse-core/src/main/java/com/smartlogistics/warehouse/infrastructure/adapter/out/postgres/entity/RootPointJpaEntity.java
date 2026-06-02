package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "root_point")
public class RootPointJpaEntity {
    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal x;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal y;

    @Column(name = "z_level", nullable = false)
    private int zLevel;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private boolean blocked;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getX() { return x; }
    public void setX(BigDecimal x) { this.x = x; }
    public BigDecimal getY() { return y; }
    public void setY(BigDecimal y) { this.y = y; }
    public int getZLevel() { return zLevel; }
    public void setZLevel(int zLevel) { this.zLevel = zLevel; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
}
