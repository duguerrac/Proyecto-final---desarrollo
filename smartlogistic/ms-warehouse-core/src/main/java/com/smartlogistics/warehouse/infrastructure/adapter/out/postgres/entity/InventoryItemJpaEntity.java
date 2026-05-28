package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory_item")
public class InventoryItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean fragile;

    @Column(name = "default_speed_limit", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultSpeedLimit;

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public boolean isFragile() { return fragile; }
    public BigDecimal getDefaultSpeedLimit() { return defaultSpeedLimit; }
}
