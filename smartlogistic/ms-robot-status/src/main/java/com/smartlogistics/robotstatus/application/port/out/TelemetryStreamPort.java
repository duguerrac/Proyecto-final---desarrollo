package com.smartlogistics.robotstatus.application.port.out;

/**
 * Hexagonal output port for streaming telemetry data to frontends.
 * Pure Java — zero framework imports.
 *
 * SSE emitter lifecycle management is handled by the infrastructure
 * adapter (SseTelemetryAdapter), not by this port.
 */
public interface TelemetryStreamPort {

    /**
     * Broadcast a telemetry event to all connected SSE clients.
     *
     * @param robotId   the robot ID
     * @param jsonData  the full JSON payload to stream
     */
    void broadcastTelemetry(String robotId, String jsonData);
}