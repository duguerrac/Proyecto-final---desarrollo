package com.smartlogistics.robotstatus.application.port.out;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hexagonal output port for streaming telemetry data to frontends.
 * Designed to be extractable to a dedicated ms-realtime-gateway (Phase 2).
 */
public interface TelemetryStreamPort {

    /**
     * Broadcast a telemetry event to all connected SSE clients.
     *
     * @param robotId   the robot ID
     * @param jsonData  the full JSON payload to stream
     */
    void broadcastTelemetry(String robotId, String jsonData);

    /**
     * Create a new SSE emitter for all-robots telemetry stream.
     *
     * @return SseEmitter for the connected client
     */
    SseEmitter createEmitter();

    /**
     * Create a new SSE emitter for a specific robot's telemetry stream.
     *
     * @param robotId the robot ID to filter events for
     * @return SseEmitter for the connected client
     */
    SseEmitter createEmitterForRobot(String robotId);
}
