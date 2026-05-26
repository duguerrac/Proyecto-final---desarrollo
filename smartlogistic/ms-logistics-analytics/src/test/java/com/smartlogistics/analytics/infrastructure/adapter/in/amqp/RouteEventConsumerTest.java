package com.smartlogistics.analytics.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogistics.analytics.application.port.in.ProcessRouteEventUseCase;
import com.smartlogistics.analytics.domain.exception.EventProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
        objectMapper.findAndRegisterModules();
        consumer = new RouteEventConsumer(useCase, objectMapper);
    }

    @Test
    void onRouteCompleted_ShouldDeserializeAndProcess() throws Exception {
        String json = """
                {
                    "eventId": "evt-001",
                    "orderId": "ORD-001",
                    "robotId": "RBT-01",
                    "path": ["A1", "B2"],
                    "distance": 150.0,
                    "duration": 45,
                    "timestamp": "2026-05-26T13:00:00Z"
                }
                """;

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                "evt-001".equals(event.getEventId()) &&
                "ORD-001".equals(event.getOrderId()) &&
                "RBT-01".equals(event.getRobotId()) &&
                event.getPath().containsAll(List.of("A1", "B2")) &&
                event.getDistance() == 150.0 &&
                event.getDuration() == 45
        ));
    }

    @Test
    void onRouteCompleted_EmptyPath_ShouldProcess() {
        String json = """
                {
                    "eventId": "evt-002",
                    "orderId": "ORD-002",
                    "robotId": "RBT-02",
                    "path": [],
                    "distance": 0.0,
                    "duration": 0,
                    "timestamp": "2026-05-26T14:00:00Z"
                }
                """;

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                event.getPath().isEmpty()
        ));
    }

    @Test
    void onRouteCompleted_NullPath_ShouldDefaultToEmpty() {
        String json = """
                {
                    "eventId": "evt-003",
                    "orderId": "ORD-003",
                    "robotId": "RBT-03",
                    "distance": 10.0,
                    "duration": 5,
                    "timestamp": "2026-05-26T15:00:00Z"
                }
                """;

        consumer.onRouteCompleted(json);

        verify(useCase, times(1)).process(argThat(event ->
                event.getPath() != null && event.getPath().isEmpty()
        ));
    }

    @Test
    void onRouteCompleted_NullTimestamp_ShouldUseNow() {
        String json = """
                {
                    "eventId": "evt-004",
                    "orderId": "ORD-004",
                    "robotId": "RBT-04",
                    "path": ["A1"],
                    "distance": 5.0,
                    "duration": 2
                }
                """;

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
                    "eventId": "evt-005",
                    "orderId": "ORD-005",
                    "robotId": "RBT-05",
                    "path": ["A1"],
                    "distance": 5.0,
                    "duration": 2,
                    "timestamp": "2026-05-26T16:00:00Z"
                }
                """;
        doThrow(new RuntimeException("Processing error"))
                .when(useCase).process(any());

        assertThrows(RuntimeException.class,
                () -> consumer.onRouteCompleted(json));
    }
}
