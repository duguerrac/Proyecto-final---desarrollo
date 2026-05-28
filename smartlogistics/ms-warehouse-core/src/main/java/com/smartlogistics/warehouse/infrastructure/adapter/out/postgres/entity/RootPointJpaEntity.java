package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "root_point")
public class RootPointJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    public String getCode() { return code; }
    public BigDecimal getX() { return x; }
    public BigDecimal getY() { return y; }
    public int getZLevel() { return zLevel; }
    public String getType() { return type; }
    public boolean isBlocked() { return blocked; }
}
