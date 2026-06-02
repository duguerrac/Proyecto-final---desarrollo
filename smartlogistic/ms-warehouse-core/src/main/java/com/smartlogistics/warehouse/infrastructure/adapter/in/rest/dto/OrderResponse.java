package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.time.Instant;
import java.util.List;

public class OrderResponse {
    private Long id;
    private String status;
    private String pickupSpotCode;
    private String deliveryPoint;
    private String robotId;
    private Instant createdAt;
    private List<OrderLineDTO> lines;

    public static class OrderLineDTO {
        private String sku;
        private Integer quantity;

        public OrderLineDTO(String sku, Integer quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
        public String getSku() { return sku; }
        public Integer getQuantity() { return quantity; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPickupSpotCode() { return pickupSpotCode; }
    public void setPickupSpotCode(String pickupSpotCode) { this.pickupSpotCode = pickupSpotCode; }
    public String getDeliveryPoint() { return deliveryPoint; }
    public void setDeliveryPoint(String deliveryPoint) { this.deliveryPoint = deliveryPoint; }
    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<OrderLineDTO> getLines() { return lines; }
    public void setLines(List<OrderLineDTO> lines) { this.lines = lines; }
}