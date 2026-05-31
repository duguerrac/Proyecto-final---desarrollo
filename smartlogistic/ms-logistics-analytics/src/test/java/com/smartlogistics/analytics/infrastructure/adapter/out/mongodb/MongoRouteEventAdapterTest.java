package com.smartlogistics.analytics.infrastructure.adapter.out.mongodb;

import com.smartlogistics.analytics.domain.model.PathPoint;
import com.smartlogistics.analytics.domain.model.RouteEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
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
        List<PathPoint> path = List.of(new PathPoint("RP-A1", BigDecimal.ONE, BigDecimal.valueOf(2)));
        RouteEvent event = new RouteEvent("evt-001", "route.completed", "ORD-001", "RBT-01",
                "WH-01", true, path, 150.0, 45, now);

        adapter.save(event);

        verify(mongoTemplate, times(1)).save(argThat(doc -> {
            if (!(doc instanceof RouteEventDocument)) return false;
            RouteEventDocument d = (RouteEventDocument) doc;
            return "evt-001".equals(d.getEventId()) &&
                    "route.completed".equals(d.getEventType()) &&
                    "ORD-001".equals(d.getOrderId()) &&
                    "RBT-01".equals(d.getRobotId()) &&
                    "WH-01".equals(d.getWarehouseId()) &&
                    d.isFragileItems() &&
                    d.getPath().equals(path) &&
                    d.getDistance() == 150.0 &&
                    d.getDuration() == 45 &&
                    now.equals(d.getTimestamp());
        }), eq("route_events"));
    }

    @Test
    void save_WithEmptyPath_ShouldMapCorrectly() {
        RouteEvent event = new RouteEvent("evt-002", "route.completed", "ORD-002", "RBT-02",
                "WH-01", false, List.of(), 0.0, 0, Instant.now());

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
        RouteEvent event = new RouteEvent("evt-003", "route.completed", "ORD-003", "RBT-03",
                "WH-01", false, List.of(new PathPoint("RP-A1", BigDecimal.ONE, BigDecimal.valueOf(2))),
                10.0, 5, null);

        adapter.save(event);

        verify(mongoTemplate, times(1)).save(argThat(doc -> {
            RouteEventDocument d = (RouteEventDocument) doc;
            return "evt-003".equals(d.getEventId()) &&
                    d.getTimestamp() == null;
        }), eq("route_events"));
    }

    @Test
    void save_MongoThrows_ShouldPropagate() {
        RouteEvent event = new RouteEvent("evt-004", "route.completed", "ORD-004", "RBT-04",
                "WH-01", false, List.of(new PathPoint("RP-A1", BigDecimal.ONE, BigDecimal.valueOf(2))),
                5.0, 2, Instant.now());
        doThrow(new RuntimeException("Mongo error"))
                .when(mongoTemplate).save(any(RouteEventDocument.class), eq("route_events"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> adapter.save(event));
    }
}
