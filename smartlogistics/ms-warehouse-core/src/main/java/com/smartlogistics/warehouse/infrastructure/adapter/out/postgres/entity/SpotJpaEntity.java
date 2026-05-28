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
    public String getAisle() { return aisle; }
    public String getSection() { return section; }
    public int getLevel() { return level; }
    public Long getRootPointId() { return rootPointId; }
}
