package com.smartlogistics.robotstatus.infrastructure.adapter.out.sse;

import com.smartlogistics.robotstatus.application.port.out.TelemetryStreamPort;
import com.smartlogistics.robotstatus.application.port.out.RobotCachePort;
import com.smartlogistics.robotstatus.domain.model.Robot;
import com.smartlogistics.robotstatus.domain.model.RobotStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE adapter that:
 * 1. Subscribes to NATS per-robot telemetry (from UE5 simulation)
 * 2. Updates Redis cache with latest robot data
 * 3. Broadcasts real-time events to all connected SSE clients
 *
 * Infrastructure layer — framework imports allowed.
 */
@Component
public class SseTelemetryAdapter implements TelemetryStreamPort {

    private static final Logger log = LoggerFactory.getLogger(SseTelemetryAdapter.class);
    private static final String TELEMETRY_SUBJECT = "smartlogistic.robot.telemetry.>";
    private static final long SSE_TIMEOUT_MS = 300_000; // 5 minutes

    private final Connection natsConnection;
    private final RobotCachePort robotCachePort;
    private final ObjectMapper objectMapper;

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Dispatcher natsDispatcher;

    public SseTelemetryAdapter(Connection natsConnection, RobotCachePort robotCachePort, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.robotCachePort = robotCachePort;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribeToNats() {
        natsDispatcher = natsConnection.createDispatcher(msg -> {
            try {
                String json = new String(msg.getData());
                @SuppressWarnings("unchecked")
                Map<String, Object> event = objectMapper.readValue(json, Map.class);

                @SuppressWarnings("unchecked")
                Map<String, Object> robotData = (Map<String, Object>) event.get("robot");
                if (robotData != null) {
                    String robotId = (String) robotData.get("id");

                    // Update Redis cache
                    updateRobotCache(robotData);

                    // Broadcast to SSE clients
                    broadcastTelemetry(robotId, json);
                }
            } catch (Exception e) {
                log.error("Failed to process NATS telemetry: {}", e.getMessage());
            }
        });

        natsDispatcher.subscribe(TELEMETRY_SUBJECT);
        log.info("[SSE] Subscribed to NATS subject: {}", TELEMETRY_SUBJECT);
    }

    private void updateRobotCache(Map<String, Object> robotData) {
        try {
            String robotId = (String) robotData.get("id");
            if (robotId == null) return;

            Robot existing = robotCachePort.findById(robotId).orElse(null);
            if (existing == null) {
                existing = new Robot();
                existing.setId(robotId);
            }

            if (robotData.get("name") != null) {
                existing.setName((String) robotData.get("name"));
            }
            if (robotData.get("batteryLevel") != null) {
                existing.setBatteryLevel(((Number) robotData.get("batteryLevel")).intValue());
            }
            if (robotData.get("available") != null) {
                existing.setAvailable((Boolean) robotData.get("available"));
            }
            if (robotData.get("currentLocation") != null) {
                existing.setCurrentLocation((String) robotData.get("currentLocation"));
            }
            if (robotData.get("operationalMode") != null) {
                try {
                    existing.setOperationalMode(RobotStatus.valueOf((String) robotData.get("operationalMode")));
                } catch (IllegalArgumentException ignored) {}
            }

            robotCachePort.save(existing);
            log.debug("[SSE] Updated Redis cache for robot {}", robotId);
        } catch (Exception e) {
            log.error("[SSE] Failed to update cache: {}", e.getMessage());
        }
    }

    @Override
    public void broadcastTelemetry(String robotId, String jsonData) {
        List<SseEmitter> deadClients = new java.util.ArrayList<>();

        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event()
                        .name("telemetry")
                        .data(jsonData)
                        .id(String.valueOf(System.currentTimeMillis())));
            } catch (Exception e) {
                deadClients.add(client);
            }
        }

        if (!deadClients.isEmpty()) {
            clients.removeAll(deadClients);
            log.debug("[SSE] Removed {} dead clients, active: {}", deadClients.size(), clients.size());
        }
    }

    @Override
    public SseEmitter createEmitter() {
        return createAndRegisterEmitter(null);
    }

    @Override
    public SseEmitter createEmitterForRobot(String robotId) {
        return createAndRegisterEmitter(robotId);
    }

    private SseEmitter createAndRegisterEmitter(String robotFilter) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> {
            clients.remove(emitter);
            log.info("[SSE] Client disconnected. Active: {}", clients.size());
        });

        emitter.onTimeout(() -> {
            clients.remove(emitter);
            log.info("[SSE] Client timeout. Active: {}", clients.size());
        });

        emitter.onError(ex -> {
            clients.remove(emitter);
            log.debug("[SSE] Client error: {}", ex.getMessage());
        });

        clients.add(emitter);
        log.info("[SSE] New client connected. Active: {}", clients.size());

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"message\":\"SSE telemetry stream active\"}"));
        } catch (Exception e) {
            clients.remove(emitter);
        }

        return emitter;
    }

    @PreDestroy
    public void cleanup() {
        if (natsDispatcher != null) {
            natsDispatcher.unsubscribe(TELEMETRY_SUBJECT);
        }
        clients.forEach(emitter -> {
            try { emitter.complete(); } catch (Exception ignored) {}
        });
        clients.clear();
        executor.shutdown();
        log.info("[SSE] Adapter shutdown complete");
    }

    public int getActiveClientCount() {
        return clients.size();
    }
}