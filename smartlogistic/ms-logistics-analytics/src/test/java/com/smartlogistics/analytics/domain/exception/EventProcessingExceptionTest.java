package com.smartlogistics.analytics.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventProcessingExceptionTest {

    @Test
    void exception_ShouldStoreMessageAndCause() {
        Throwable cause = new RuntimeException("test cause");
        EventProcessingException ex = new EventProcessingException("test message", cause);

        assertEquals("test message", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void exception_ShouldBeRuntimeException() {
        EventProcessingException ex = new EventProcessingException("msg", new RuntimeException());

        assertInstanceOf(RuntimeException.class, ex);
    }
}
