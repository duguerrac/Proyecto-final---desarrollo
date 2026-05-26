package com.smartlogistics.analytics.infrastructure.adapter.out.mongodb;

import com.smartlogistics.analytics.domain.model.RouteEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

class MongoRouteEventAdapterTest {

    private MongoTemplate mongoTemplate;
    private MongoRouteEventAdapter adapter;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        adapter = new MongoRouteEventAdapter(mongoTemplate);
    }

    @Test
    void save_ShouldMapAndPersist() {
        Instant now = Instant.now();
        RouteEvent event = new RouteEvent("evt-001", "ORD-001", "RBT-01",
                List.of("A1", "B2"), 150.0, 45, now);

        adapter.save(event);

        verify(mongoTemplate, times(1)).save(argThat(doc -> {
            if (!(doc instanceof RouteEventDocument)) return false;
            RouteEventDocument d = (RouteEventDocument) doc;
            return "evt-001".equals(d.getEventId()) &&
                    "ORD-001".equals(d.getOrderId()) &&
                    "RBT-01".equals(d.getRobotId()) &&
                    d.getPath().containsAll(List.of("A1", "B2")) &&
                    d.getDistance() == 150.0 &&
                    d.getDuration() == 45 &&
                    now.equals(d.getTimestamp());
        }), eq("route_events"));
    }

    @Test
    void save_WithEmptyPath_ShouldMapCorrectly() {
        RouteEvent event = new RouteEvent("evt-002", "ORD-002", "RBT-02",
                List.of(), 0.0, 0, Instant.now());

        adapter.save(event);

        verify(mongoTemplate, times(1)).save(argThat(doc -> {
            RouteEventDocument d = (RouteEventDocument) doc;
            return "evt-002".equals(d.getEventId()) &&
                    d.getPath().isEmpty() &&
                    d.getDistance() == 0.0 &&
                    d.getDuration() == 0;
        }), eq("route_events"));
    }

    @Test
    void save_WithNullTimestamp_ShouldMapCorrectly() {
        RouteEvent event = new RouteEvent("evt-003", "ORD-003", "RBT-03",
                List.of("A1"), 10.0, 5, null);

        adapter.save(event);

        verify(mongoTemplate, times(1)).save(argThat(doc -> {
            RouteEventDocument d = (RouteEventDocument) doc;
            return "evt-003".equals(d.getEventId()) &&
                    d.getTimestamp() == null;
        }), eq("route_events"));
    }

    @Test
    void save_MongoThrows_ShouldPropagate() {
        RouteEvent event = new RouteEvent("evt-004", "ORD-004", "RBT-04",
                List.of("A1"), 5.0, 2, Instant.now());
        doThrow(new RuntimeException("Mongo error"))
                .when(mongoTemplate).save(any(RouteEventDocument.class), eq("route_events"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> adapter.save(event));
    }
}
