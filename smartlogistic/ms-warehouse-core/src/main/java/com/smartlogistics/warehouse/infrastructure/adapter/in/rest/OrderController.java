package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.OrderResponse;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.OrderLineJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.WarehouseOrderJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.WarehouseOrderJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final WarehouseOrderJpaRepository orderRepo;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public OrderController(WarehouseOrderJpaRepository orderRepo,
                           RabbitTemplate rabbitTemplate,
                           @Value("${rabbitmq.exchange:logistics.exchange}") String exchange) {
        this.orderRepo = orderRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest req) {
        WarehouseOrderJpaEntity order = new WarehouseOrderJpaEntity();
        order.setPickupSpotCode(req.getPickupSpotCode());
        order.setDeliveryPoint(req.getDeliveryPoint());

        for (CreateOrderRequest.OrderLineDTO line : req.getLines()) {
            OrderLineJpaEntity ol = new OrderLineJpaEntity();
            ol.setSku(line.getSku());
            ol.setQuantity(line.getQuantity());
            order.getLines().add(ol);
        }

        order = orderRepo.save(order);

        // Publish order.created event via RabbitMQ
        String payload = buildOrderCreatedPayload(order);
        rabbitTemplate.convertAndSend(exchange, "order.created", payload);
        log.info("[RabbitMQ] Published order.created: {}", payload);

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

            return ResponseEntity.ok(toResponse(order));
        }).orElse(ResponseEntity.notFound().build());
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
        sb.append(",\"pickupSpotCode\":\"").append(order.getPickupSpotCode()).append("\"");
        sb.append(",\"deliveryPoint\":\"").append(order.getDeliveryPoint()).append("\"");
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