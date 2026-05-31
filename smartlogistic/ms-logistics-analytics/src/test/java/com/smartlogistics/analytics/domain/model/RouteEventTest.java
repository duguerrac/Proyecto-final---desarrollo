package com.smartlogistics.analytics.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteEventTest {

    @Test
    void constructor_ShouldSetAllFields() {
        Instant now = Instant.now();
        List<PathPoint> path = List.of(new PathPoint("RP-A1", BigDecimal.valueOf(1), BigDecimal.valueOf(2)));
        RouteEvent event = new RouteEvent("evt-001", "route.completed", "ORD-001", "RBT-01", "WH-01", true, path, 150.0, 45, now);

        assertEquals("evt-001", event.getEventId());
        assertEquals("route.completed", event.getEventType());
        assertEquals("ORD-001", event.getOrderId());
        assertEquals("RBT-01", event.getRobotId());
        assertEquals("WH-01", event.getWarehouseId());
        assertTrue(event.isFragileItems());
        assertEquals(path, event.getPath());
        assertEquals(150.0, event.getDistance());
        assertEquals(45, event.getDuration());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void constructor_ShouldAcceptEmptyPath() {
        RouteEvent event = new RouteEvent("evt-002", "route.completed", "ORD-002", "RBT-02", "WH-01", false, List.of(), 0.0, 0, Instant.now());

        assertTrue(event.getPath().isEmpty());
    }

    @Test
    void constructor_ShouldAcceptNullTimestamp() {
        RouteEvent event = new RouteEvent("evt-003", "route.completed", "ORD-003", "RBT-03", "WH-01", false, List.of(), 10.0, 5, null);

        assertNull(event.getTimestamp());
    }

    @Test
    void constructor_ShouldAcceptNegativeDistance() {
        RouteEvent event = new RouteEvent("evt-004", "route.completed", "ORD-004", "RBT-04", "WH-01", false, List.of(), -1.0, -5, Instant.now());

        assertEquals(-1.0, event.getDistance());
        assertEquals(-5, event.getDuration());
    }

    @Test
    void path_ShouldBeImmutable() {
        List<PathPoint> mutablePath = new java.util.ArrayList<>(List.of(new PathPoint("RP-A1", BigDecimal.ONE, BigDecimal.valueOf(2))));
        RouteEvent event = new RouteEvent("evt-005", "route.completed", "ORD-005", "RBT-05", "WH-01", false, mutablePath, 5.0, 1, Instant.now());

        mutablePath.add(new PathPoint("RP-B2", BigDecimal.valueOf(3), BigDecimal.valueOf(4)));

        assertEquals(1, event.getPath().size());
    }

    @Test
    void equalsAndHashCode_ShouldUseReferenceEquality() {
        Instant now = Instant.now();
        List<PathPoint> path = List.of(new PathPoint("RP-A1", BigDecimal.ONE, BigDecimal.valueOf(2)));
        RouteEvent event1 = new RouteEvent("evt-001", "route.completed", "ORD-001", "RBT-01", "WH-01", false, path, 10.0, 5, now);
        RouteEvent event2 = new RouteEvent("evt-001", "route.completed", "ORD-001", "RBT-01", "WH-01", false, path, 10.0, 5, now);

        assertNotEquals(event1, event2);
        assertNotEquals(event1.hashCode(), event2.hashCode());
    }
}
