package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory_item")
public class InventoryItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean fragile = false;

    @Column(name = "default_speed_limit", precision = 5, scale = 2)
    private BigDecimal defaultSpeedLimit;

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public boolean isFragile() { return fragile; }
    public void setFragile(boolean fragile) { this.fragile = fragile; }
    public BigDecimal getDefaultSpeedLimit() { return defaultSpeedLimit; }
    public void setDefaultSpeedLimit(BigDecimal defaultSpeedLimit) { this.defaultSpeedLimit = defaultSpeedLimit; }
}
