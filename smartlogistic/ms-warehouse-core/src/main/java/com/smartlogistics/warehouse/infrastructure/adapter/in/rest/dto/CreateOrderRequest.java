package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

import java.util.List;

public class CreateOrderRequest {
    private String pickupSpotCode;
    private String deliveryPoint;
    private List<OrderLineDTO> lines;

    public static class OrderLineDTO {
        private String sku;
        private Integer quantity;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public String getPickupSpotCode() { return pickupSpotCode; }
    public void setPickupSpotCode(String pickupSpotCode) { this.pickupSpotCode = pickupSpotCode; }
    public String getDeliveryPoint() { return deliveryPoint; }
    public void setDeliveryPoint(String deliveryPoint) { this.deliveryPoint = deliveryPoint; }
    public List<OrderLineDTO> getLines() { return lines; }
    public void setLines(List<OrderLineDTO> lines) { this.lines = lines; }
}