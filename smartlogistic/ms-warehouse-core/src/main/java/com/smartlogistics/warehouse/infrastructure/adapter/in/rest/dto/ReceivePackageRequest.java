package com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto;

public class ReceivePackageRequest {
    private String sku;
    private int quantity;
    private String receptionSpotCode;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getReceptionSpotCode() { return receptionSpotCode; }
    public void setReceptionSpotCode(String code) { this.receptionSpotCode = code; }
}