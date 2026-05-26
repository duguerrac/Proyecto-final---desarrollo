package com.smartlogistics.analytics.domain.exception;

public class EventProcessingException extends RuntimeException {
    public EventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
