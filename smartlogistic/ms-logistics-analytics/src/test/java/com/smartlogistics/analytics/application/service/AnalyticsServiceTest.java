package com.smartlogistics.analytics.application.service;

import com.smartlogistics.analytics.application.port.out.AnalyticsRepositoryPort;
import com.smartlogistics.analytics.domain.model.RouteEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private AnalyticsRepositoryPort repository;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalyticsRepositoryPort.class);
        service = new AnalyticsService(repository);
    }

    @Test
    void process_ShouldSaveEvent() {
        RouteEvent event = new RouteEvent("evt-001", "ORD-001", "RBT-01",
                List.of("A1", "B2"), 150.0, 45, Instant.now());

        service.process(event);

        verify(repository, times(1)).save(event);
    }

    @Test
    void process_NullEvent_ShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.process(null));
        verifyNoInteractions(repository);
    }

    @Test
    void process_EmptyPath_ShouldSave() {
        RouteEvent event = new RouteEvent("evt-002", "ORD-002", "RBT-02",
                List.of(), 0.0, 0, Instant.now());

        service.process(event);

        verify(repository, times(1)).save(event);
    }

    @Test
    void process_NullTimestamp_ShouldSave() {
        RouteEvent event = new RouteEvent("evt-003", "ORD-003", "RBT-03",
                List.of("A1"), 10.0, 5, null);

        service.process(event);

        verify(repository, times(1)).save(event);
    }

    @Test
    void process_LargeDistance_ShouldSave() {
        RouteEvent event = new RouteEvent("evt-004", "ORD-004", "RBT-04",
                List.of("A1", "B2", "C3", "D4"), 999999.99, 3600, Instant.now());

        service.process(event);

        verify(repository, times(1)).save(event);
    }

    @Test
    void process_RepositoryThrows_ShouldPropagate() {
        RouteEvent event = new RouteEvent("evt-005", "ORD-005", "RBT-05",
                List.of("A1"), 1.0, 1, Instant.now());
        doThrow(new RuntimeException("DB error")).when(repository).save(event);

        assertThrows(RuntimeException.class, () -> service.process(event));
    }

    @Test
    void process_MultipleEvents_ShouldSaveEach() {
        RouteEvent event1 = new RouteEvent("evt-001", "ORD-001", "RBT-01",
                List.of("A1"), 10.0, 1, Instant.now());
        RouteEvent event2 = new RouteEvent("evt-002", "ORD-002", "RBT-02",
                List.of("B1"), 20.0, 2, Instant.now());

        service.process(event1);
        service.process(event2);

        verify(repository, times(1)).save(event1);
        verify(repository, times(1)).save(event2);
    }
}
