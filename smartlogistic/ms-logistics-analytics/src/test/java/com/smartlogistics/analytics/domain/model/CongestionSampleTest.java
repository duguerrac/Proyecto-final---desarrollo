package com.smartlogistics.analytics.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CongestionSampleTest {

    @Test
    void constructor_ShouldSetAllFields() {
        Instant now = Instant.now();
        CongestionSample sample = new CongestionSample("ZONE-A", 5, 2.5, now);

        assertEquals("ZONE-A", sample.getZoneId());
        assertEquals(5, sample.getRobotCount());
        assertEquals(2.5, sample.getAverageWaitTime());
        assertEquals(now, sample.getTimestamp());
    }

    @Test
    void constructor_ShouldAcceptZeroRobotCount() {
        CongestionSample sample = new CongestionSample("ZONE-B", 0, 0.0, Instant.now());

        assertEquals(0, sample.getRobotCount());
        assertEquals(0.0, sample.getAverageWaitTime());
    }

    @Test
    void constructor_ShouldAcceptNullTimestamp() {
        CongestionSample sample = new CongestionSample("ZONE-C", 3, 1.0, null);

        assertNull(sample.getTimestamp());
    }
}
