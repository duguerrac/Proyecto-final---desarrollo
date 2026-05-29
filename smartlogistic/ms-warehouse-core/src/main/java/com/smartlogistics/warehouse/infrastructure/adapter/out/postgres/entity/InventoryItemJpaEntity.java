package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;

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

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
}