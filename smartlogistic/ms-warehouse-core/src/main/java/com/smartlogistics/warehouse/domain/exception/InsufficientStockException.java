package com.smartlogistics.warehouse.domain.exception;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
