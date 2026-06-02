package com.smartlogistics.warehouse.infrastructure.adapter.out.sse;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE adapter that broadcasts package events to connected frontend clients.
 * Events: package-received, package-updated
 */
@Component
public class SsePackageAdapter {

    private static final Logger log = LoggerFactory.getLogger(SsePackageAdapter.class);
    private static final long SSE_TIMEOUT_MS = 300_000; // 5 minutes

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();

    /**
     * Broadcast a package event to all connected SSE clients.
     */
    public void broadcastPackageEvent(String eventType, String jsonData) {
        List<SseEmitter> deadClients = new java.util.ArrayList<>();

        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event()
                        .name(eventType)
                        .data(jsonData)
                        .id(String.valueOf(System.currentTimeMillis())));
            } catch (Exception e) {
                deadClients.add(client);
            }
        }

        if (!deadClients.isEmpty()) {
            clients.removeAll(deadClients);
            log.debug("[SSE-Pkg] Removed {} dead clients, active: {}", deadClients.size(), clients.size());
        }
    }

    /**
     * Create a new SSE emitter for package event stream.
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> {
            clients.remove(emitter);
            log.info("[SSE-Pkg] Client disconnected. Active: {}", clients.size());
        });

        emitter.onTimeout(() -> {
            clients.remove(emitter);
            log.info("[SSE-Pkg] Client timeout. Active: {}", clients.size());
        });

        emitter.onError(ex -> {
            clients.remove(emitter);
            log.debug("[SSE-Pkg] Client error: {}", ex.getMessage());
        });

        clients.add(emitter);
        log.info("[SSE-Pkg] New client connected. Active: {}", clients.size());

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"message\":\"SSE package stream active\"}"));
        } catch (Exception e) {
            clients.remove(emitter);
        }

        return emitter;
    }

    @PreDestroy
    public void cleanup() {
        clients.forEach(emitter -> {
            try { emitter.complete(); } catch (Exception ignored) {}
        });
        clients.clear();
        log.info("[SSE-Pkg] Adapter shutdown complete");
    }

    public int getActiveClientCount() {
        return clients.size();
    }
}