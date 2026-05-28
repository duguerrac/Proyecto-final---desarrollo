package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "order_item")
public class OrderItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getItemId() { return itemId; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public void setRequestedQuantity(int requestedQuantity) { this.requestedQuantity = requestedQuantity; }
}
