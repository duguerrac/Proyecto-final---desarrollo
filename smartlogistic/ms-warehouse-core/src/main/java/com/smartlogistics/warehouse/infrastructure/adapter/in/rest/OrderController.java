package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.OrderResponse;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.OrderLineJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.WarehouseOrderJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RootPointJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.SpotItemJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.SpotJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.WarehouseOrderJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.sse.SsePackageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final WarehouseOrderJpaRepository orderRepo;
    private final SpotItemJpaRepository spotItemRepo;
    private final SpotJpaRepository spotRepo;
    private final RootPointJpaRepository rootPointRepo;
    private final RabbitTemplate rabbitTemplate;
    private final SsePackageAdapter ssePackageAdapter;
    private final String exchange;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public OrderController(WarehouseOrderJpaRepository orderRepo,
                           SpotItemJpaRepository spotItemRepo,
                           SpotJpaRepository spotRepo,
                           RootPointJpaRepository rootPointRepo,
                           RabbitTemplate rabbitTemplate,
                           SsePackageAdapter ssePackageAdapter,
                           @Value("${rabbitmq.exchange:logistics.exchange}") String exchange) {
        this.orderRepo = orderRepo;
        this.spotItemRepo = spotItemRepo;
        this.spotRepo = spotRepo;
        this.rootPointRepo = rootPointRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.ssePackageAdapter = ssePackageAdapter;
        this.exchange = exchange;
    }

    // ─── RabbitMQ Listeners ──────────────────────────────────────

    @RabbitListener(queues = "order.status_changed")
    public void onOrderStatusChanged(String json) {
        log.info("[RabbitMQ] order.status_changed received: {}", json);
        try {
            var tree = objectMapper.readTree(json);
            Long orderId = tree.get("orderId").asLong();
            String status = tree.has("status") ? tree.get("status").asText() : "";
            String robotId = tree.has("robotId") ? tree.get("robotId").asText() : null;

            orderRepo.findById(orderId).ifPresent(order -> {
                order.setStatus(status);
                if (robotId != null && !robotId.isEmpty()) {
                    order.setRobotId(robotId);
                }
                orderRepo.save(order);
                log.info("[RabbitMQ] Order {} → status={}, robotId={}", orderId, status, robotId);

                broadcastOrderUpdate("order-updated", order);
            });
        } catch (Exception e) {
            log.error("Error processing order.status_changed", e);
        }
    }

    // ─── REST Endpoints ──────────────────────────────────────────

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest req) {
        // Auto-resolve pickupSpotCode from SKU if not provided
        String pickupSpotCode = req.getPickupSpotCode();
        if ((pickupSpotCode == null || pickupSpotCode.isBlank()) && req.getLines() != null && !req.getLines().isEmpty()) {
            String firstSku = req.getLines().get(0).getSku();
            List<Object[]> spots = spotItemRepo.findSpotsBySku(firstSku);
            if (!spots.isEmpty()) {
                pickupSpotCode = (String) spots.get(0)[1]; // spot code (SP- format)
                log.info("[Auto-resolve] SKU '{}' found at spot '{}'", firstSku, pickupSpotCode);
            } else {
                log.warn("[Auto-resolve] SKU '{}' not found in any spot", firstSku);
            }
        }

        // Resolve SP- code to RP- code so UE5 can use it for route planning
        String resolvedPickupCode = resolveToRootPointCode(pickupSpotCode);

        WarehouseOrderJpaEntity order = new WarehouseOrderJpaEntity();
        order.setPickupSpotCode(resolvedPickupCode);
        order.setDeliveryPoint(req.getDeliveryPoint() != null ? req.getDeliveryPoint() : "RP-R00-C09");

        for (CreateOrderRequest.OrderLineDTO line : req.getLines()) {
            OrderLineJpaEntity ol = new OrderLineJpaEntity();
            ol.setSku(line.getSku());
            ol.setQuantity(line.getQuantity());
            ol.setOrder(order);  // set bidirectional reference
            order.getLines().add(ol);
        }

        order = orderRepo.save(order);

        // Publish order.created event via RabbitMQ
        String payload = buildOrderCreatedPayload(order);
        rabbitTemplate.convertAndSend(exchange, "order.dispatched", payload);
        log.info("[RabbitMQ] Published order.dispatched: {}", payload);

        broadcastOrderUpdate("order-created", order);

        return ResponseEntity.ok(toResponse(order));
    }

    @GetMapping
    public List<OrderResponse> listAll() {
        return orderRepo.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return orderRepo.findById(id)
                .map(o -> ResponseEntity.ok(toResponse(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public List<OrderResponse> getByStatus(@PathVariable String status) {
        return orderRepo.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id,
                                                      @RequestBody java.util.Map<String, String> body) {
        return orderRepo.findById(id).map(order -> {
            String newStatus = body.get("status");
            String robotId = body.get("robotId");

            order.setStatus(newStatus);
            if (robotId != null) {
                order.setRobotId(robotId);
            }
            order = orderRepo.save(order);

            // Publish status change event via RabbitMQ
            String event = String.format(
                "{\"orderId\":%d,\"status\":\"%s\",\"robotId\":\"%s\"}",
                order.getId(), order.getStatus(), order.getRobotId() != null ? order.getRobotId() : "");
            rabbitTemplate.convertAndSend(exchange, "order.status_changed", event);
            log.info("[RabbitMQ] Published order.status_changed: {}", event);

            broadcastOrderUpdate("order-updated", order);

            return ResponseEntity.ok(toResponse(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * SSE stream endpoint for real-time order events.
     * Frontend connects to /api/orders/stream to receive events:
     * - order-created: new order created
     * - order-updated: order status changed (DISPATCHED, IN_PROGRESS, COMPLETED)
     */
    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamOrders() {
        log.info("[SSE-Order] New SSE stream connection request");
        return ssePackageAdapter.createEmitter();
    }

    // ─── Helper Methods ──────────────────────────────────────────

    /**
     * Resolves a SPOT code (e.g. SP-A1-01) to its parent ROOT POINT code (e.g. RP-R02-C01).
     * If the code is already a root point code (starts with "RP-"), returns it as-is.
     */
    private String resolveToRootPointCode(String code) {
        if (code == null || code.isEmpty()) return code;
        if (code.startsWith("RP-")) {
            return code; // already a root point code
        }
        return spotRepo.findByCode(code)
                .map(SpotJpaEntity::getRootPointId)
                .flatMap(rpId -> rootPointRepo.findById(rpId))
                .map(RootPointJpaEntity::getCode)
                .orElse(code); // fallback: return original code
    }

    /**
     * Broadcast an order event via SSE to all connected frontend clients.
     */
    private void broadcastOrderUpdate(String eventType, WarehouseOrderJpaEntity order) {
        try {
            String json = objectMapper.writeValueAsString(toResponse(order));
            ssePackageAdapter.broadcastPackageEvent(eventType, json);
            log.info("[SSE-Order] Broadcast {} for order {}", eventType, order.getId());
        } catch (Exception e) {
            log.warn("[SSE-Order] Failed to broadcast {}: {}", eventType, e.getMessage());
        }
    }

    private OrderResponse toResponse(WarehouseOrderJpaEntity order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setStatus(order.getStatus());
        r.setPickupSpotCode(order.getPickupSpotCode());
        r.setDeliveryPoint(order.getDeliveryPoint());
        r.setRobotId(order.getRobotId());
        r.setCreatedAt(order.getCreatedAt());
        r.setLines(order.getLines().stream()
                .map(l -> new OrderResponse.OrderLineDTO(l.getSku(), l.getQuantity()))
                .collect(Collectors.toList()));
        return r;
    }

    private String buildOrderCreatedPayload(WarehouseOrderJpaEntity order) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"orderId\":").append(order.getId());
        sb.append(",\"status\":\"").append(order.getStatus()).append("\"");
        sb.append(",\"pickupSpotCode\":\"").append(order.getPickupSpotCode() != null ? order.getPickupSpotCode() : "").append("\"");
        sb.append(",\"deliveryPoint\":\"").append(order.getDeliveryPoint() != null ? order.getDeliveryPoint() : "").append("\"");
        sb.append(",\"lines\":[");
        for (int i = 0; i < order.getLines().size(); i++) {
            OrderLineJpaEntity l = order.getLines().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"sku\":\"").append(l.getSku()).append("\"");
            sb.append(",\"quantity\":").append(l.getQuantity()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}