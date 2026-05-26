package com.smartlogistics.analytics.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteEventTest {

    @Test
    void constructor_ShouldSetAllFields() {
        Instant now = Instant.now();
        List<String> path = List.of("A1", "B2", "C3");
        RouteEvent event = new RouteEvent("evt-001", "ORD-001", "RBT-01", path, 150.0, 45, now);

        assertEquals("evt-001", event.getEventId());
        assertEquals("ORD-001", event.getOrderId());
        assertEquals("RBT-01", event.getRobotId());
        assertEquals(path, event.getPath());
        assertEquals(150.0, event.getDistance());
        assertEquals(45, event.getDuration());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void constructor_ShouldAcceptEmptyPath() {
        RouteEvent event = new RouteEvent("evt-002", "ORD-002", "RBT-02", List.of(), 0.0, 0, Instant.now());

        assertTrue(event.getPath().isEmpty());
    }

    @Test
    void constructor_ShouldAcceptNullTimestamp() {
        RouteEvent event = new RouteEvent("evt-003", "ORD-003", "RBT-03", List.of(), 10.0, 5, null);

        assertNull(event.getTimestamp());
    }

    @Test
    void constructor_ShouldAcceptNegativeDistance() {
        RouteEvent event = new RouteEvent("evt-004", "ORD-004", "RBT-04", List.of(), -1.0, -5, Instant.now());

        assertEquals(-1.0, event.getDistance());
        assertEquals(-5, event.getDuration());
    }

    @Test
    void path_ShouldBeImmutable() {
        List<String> mutablePath = new java.util.ArrayList<>(List.of("A1"));
        RouteEvent event = new RouteEvent("evt-005", "ORD-005", "RBT-05", mutablePath, 5.0, 1, Instant.now());

        mutablePath.add("B2");

        assertEquals(1, event.getPath().size());
    }

    @Test
    void equalsAndHashCode_ShouldUseReferenceEquality() {
        Instant now = Instant.now();
        RouteEvent event1 = new RouteEvent("evt-001", "ORD-001", "RBT-01", List.of("A1"), 10.0, 5, now);
        RouteEvent event2 = new RouteEvent("evt-001", "ORD-001", "RBT-01", List.of("A1"), 10.0, 5, now);

        assertNotEquals(event1, event2);
        assertNotEquals(event1.hashCode(), event2.hashCode());
    }
}
