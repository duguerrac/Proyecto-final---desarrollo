package com.smartlogistics.analytics.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartlogistics.analytics.application.port.in.ProcessRouteEventUseCase;
import com.smartlogistics.analytics.domain.exception.EventProcessingException;
import com.smartlogistics.analytics.domain.model.PathPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteEventConsumerTest {

    private ProcessRouteEventUseCase useCase;
    private ObjectMapper objectMapper;
    private RouteEventConsumer consumer;

    @BeforeEach
    void setUp() {
        useCase = mock(ProcessRouteEventUseCase.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new RouteEventConsumer(useCase, objectMapper);
    }

    @Test
    void onRouteCompleted_ShouldDeserializeAndProcess() throws Exception {
        String json = """
                {
                    "eventId": "%s",
                    "eventType": "route.completed",
                    "occurredAt": "2026-05-26T13:00:00Z",
                    "orderId": 1,
                    "robotId": "RBT-01",
                    "warehouseId": "WH-01",
                    "fragileItems": true,
                    "totalDistanceMeters": 150.0,
                    "durationSeconds": 45,
                    "path": [{"rootPointId": "RP-START", "x": 0.0, "y": 0.0}]
                }
                """.formatted(UUID.randomUUID().toString());

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                "route.completed".equals(event.getEventType()) &&
                "RBT-01".equals(event.getRobotId()) &&
                "WH-01".equals(event.getWarehouseId()) &&
                event.isFragileItems() &&
                event.getDistance() == 150.0 &&
                event.getDuration() == 45 &&
                event.getPath().size() == 1
        ));
    }

    @Test
    void onRouteCompleted_EmptyPath_ShouldProcess() {
        String json = """
                {
                    "eventId": "%s",
                    "eventType": "route.completed",
                    "occurredAt": "2026-05-26T14:00:00Z",
                    "orderId": 2,
                    "robotId": "RBT-02",
                    "warehouseId": "WH-01",
                    "fragileItems": false,
                    "totalDistanceMeters": 0.0,
                    "durationSeconds": 0,
                    "path": []
                }
                """.formatted(UUID.randomUUID().toString());

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                event.getPath().isEmpty()
        ));
    }

    @Test
    void onRouteCompleted_NullPath_ShouldDefaultToEmpty() {
        String json = """
                {
                    "eventId": "%s",
                    "eventType": "route.completed",
                    "occurredAt": "2026-05-26T15:00:00Z",
                    "orderId": 3,
                    "robotId": "RBT-03",
                    "warehouseId": "WH-01",
                    "fragileItems": false,
                    "totalDistanceMeters": 10.0,
                    "durationSeconds": 5
                }
                """.formatted(UUID.randomUUID().toString());

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                event.getPath() != null && event.getPath().isEmpty() &&
                event.getDistance() == 10.0 &&
                event.getDuration() == 5
        ));
    }

    @Test
    void onRouteCompleted_NullOccurredAt_ShouldUseNow() {
        String json = """
                {
                    "eventId": "%s",
                    "eventType": "route.completed",
                    "orderId": 4,
                    "robotId": "RBT-04",
                    "warehouseId": "WH-01",
                    "fragileItems": false,
                    "totalDistanceMeters": 5.0,
                    "durationSeconds": 2,
                    "path": [{"rootPointId": "RP-A1", "x": 1.0, "y": 2.0}]
                }
                """.formatted(UUID.randomUUID().toString());

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                event.getTimestamp() != null
        ));
    }

    @Test
    void onRouteCompleted_InvalidJson_ShouldThrowEventProcessingException() {
        String invalidJson = "{this is not json}";

        assertThrows(EventProcessingException.class,
                () -> consumer.onRouteCompleted(invalidJson));
        verifyNoInteractions(useCase);
    }

    @Test
    void onRouteCompleted_InvalidJsonStructure_ShouldThrow() {
        String json = """
                {"bad": "data", "not": "valid"}
                """;

        assertThrows(EventProcessingException.class,
                () -> consumer.onRouteCompleted(json));
        verifyNoInteractions(useCase);
    }

    @Test
    void onRouteCompleted_UseCaseThrows_ShouldPropagate() {
        String json = """
                {
                    "eventId": "%s",
                    "eventType": "route.completed",
                    "occurredAt": "2026-05-26T16:00:00Z",
                    "orderId": 5,
                    "robotId": "RBT-05",
                    "warehouseId": "WH-01",
                    "fragileItems": false,
                    "totalDistanceMeters": 5.0,
                    "durationSeconds": 2,
                    "path": [{"rootPointId": "RP-A1", "x": 1.0, "y": 2.0}]
                }
                """.formatted(UUID.randomUUID().toString());
        doThrow(new RuntimeException("Processing error"))
                .when(useCase).process(any());

        assertThrows(RuntimeException.class,
                () -> consumer.onRouteCompleted(json));
    }
}
