package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "spot_item", uniqueConstraints = @UniqueConstraint(columnNames = {"spot_id", "item_id"}))
public class SpotItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    public Long getId() { return id; }
    public Long getSpotId() { return spotId; }
    public Long getItemId() { return itemId; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public int getQuantityReserved() { return quantityReserved; }
    public void setQuantityAvailable(int quantityAvailable) { this.quantityAvailable = quantityAvailable; }
    public void setQuantityReserved(int quantityReserved) { this.quantityReserved = quantityReserved; }
}
