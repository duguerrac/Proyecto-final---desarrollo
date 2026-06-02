package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.PackageResponse;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.ReceivePackageRequest;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.IncomingPackageJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.InventoryItemJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.OrderLineJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotItemJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.WarehouseOrderJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.IncomingPackageJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.InventoryItemJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.RootPointJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.SpotItemJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.SpotJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.WarehouseOrderJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.sse.SsePackageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private static final Logger log = LoggerFactory.getLogger(PackageController.class);

    private final IncomingPackageJpaRepository packageRepo;
    private final InventoryItemJpaRepository itemRepo;
    private final SpotJpaRepository spotRepo;
    private final SpotItemJpaRepository spotItemRepo;
    private final RootPointJpaRepository rootPointRepo;
    private final WarehouseOrderJpaRepository orderRepo;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final SsePackageAdapter ssePackageAdapter;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public PackageController(IncomingPackageJpaRepository packageRepo,
                             InventoryItemJpaRepository itemRepo,
                             SpotJpaRepository spotRepo,
                             SpotItemJpaRepository spotItemRepo,
                             RootPointJpaRepository rootPointRepo,
                             WarehouseOrderJpaRepository orderRepo,
                             RabbitTemplate rabbitTemplate,
                             SsePackageAdapter ssePackageAdapter,
                             @Value("${rabbitmq.exchange:logistics.exchange}") String exchange) {
        this.packageRepo = packageRepo;
        this.itemRepo = itemRepo;
        this.spotRepo = spotRepo;
        this.spotItemRepo = spotItemRepo;
        this.rootPointRepo = rootPointRepo;
        this.orderRepo = orderRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.ssePackageAdapter = ssePackageAdapter;
        this.exchange = exchange;
    }

    // ─── RabbitMQ Listeners (replacing NATS subscriptions) ───

    @RabbitListener(queuesToDeclare = @Queue(name = "package.taken", durable = "true"))
    public void onPackageTaken(String json) {
        log.info("[RabbitMQ] package.taken received: {}", json);
        try {
            var tree = objectMapper.readTree(json);
            Long packageId = tree.get("packageId").asLong();
            String robotId = tree.get("robotId").asText();

            packageRepo.findById(packageId).ifPresent(pkg -> {
                pkg.setStatus("IN_TRANSIT");
                pkg.setRobotId(robotId);
                packageRepo.save(pkg);
                log.info("[RabbitMQ] Package {} → IN_TRANSIT (robot={})", packageId, robotId);

                // Broadcast SSE event to frontend
                broadcastPackageUpdate("package-updated", pkg);
            });
        } catch (Exception e) {
            log.error("Error processing package.taken", e);
        }
    }

    @RabbitListener(queuesToDeclare = @Queue(name = "package.delivered", durable = "true"))
    public void onPackageDelivered(String json) {
        log.info("[RabbitMQ] package.delivered received: {}", json);
        try {
            var tree = objectMapper.readTree(json);
            Long packageId = tree.get("packageId").asLong();
            String missionType = tree.has("missionType") ? tree.get("missionType").asText() : "";

            if ("STOCK_OUT".equals(missionType)) {
                // STOCK_OUT: Items were picked from shelf and delivered to dispatch dock.
                // packageId is actually the orderId in this case.
                handleStockOutDelivered(packageId);
            } else {
                // STOCK_IN (default): Items were delivered to a shelf spot — increment stock
                handleStockInDelivered(packageId);
            }
        } catch (Exception e) {
            log.error("Error processing package.delivered", e);
        }
    }

    private void handleStockInDelivered(Long packageId) {
        packageRepo.findById(packageId).ifPresent(pkg -> {
            SpotJpaEntity spot = findSpotByCode(pkg.getTargetSpotCode());

            SpotItemJpaEntity spotItem = spotItemRepo
                    .findBySpotIdAndItemId(spot.getId(), pkg.getItemId())
                    .orElseGet(() -> {
                        SpotItemJpaEntity si = new SpotItemJpaEntity();
                        si.setSpotId(spot.getId());
                        si.setItemId(pkg.getItemId());
                        si.setQuantityReserved(0);
                        return si;
                    });

            spotItem.setQuantityAvailable(spotItem.getQuantityAvailable() + pkg.getQuantity());
            spotItemRepo.save(spotItem);

            pkg.setStatus("DELIVERED");
            packageRepo.save(pkg);
            log.info("[RabbitMQ] STOCK_IN Package {} → DELIVERED. Added {}x SKU {} to spot {}",
                    packageId, pkg.getQuantity(), pkg.getSku(), pkg.getTargetSpotCode());

            broadcastPackageUpdate("package-updated", pkg);
            broadcastStockUpdate(spot.getId());
        });
    }

    private void handleStockOutDelivered(Long orderId) {
        orderRepo.findById(orderId).ifPresent(order -> {
            String pickupSpotCode = order.getPickupSpotCode();
            SpotJpaEntity spot = findSpotByCode(pickupSpotCode);

            for (OrderLineJpaEntity line : order.getLines()) {
                // Find the inventory item by SKU to get its ID
                itemRepo.findBySku(line.getSku()).ifPresent(item -> {
                    SpotItemJpaEntity spotItem = spotItemRepo
                            .findBySpotIdAndItemId(spot.getId(), item.getId())
                            .orElse(null);

                    if (spotItem != null) {
                        int newQty = Math.max(0, spotItem.getQuantityAvailable() - line.getQuantity());
                        spotItem.setQuantityAvailable(newQty);
                        spotItemRepo.save(spotItem);
                        log.info("[RabbitMQ] STOCK_OUT Order {} → Decremented {}x SKU {} from spot {} (now {})",
                                orderId, line.getQuantity(), line.getSku(), pickupSpotCode, newQty);
                    } else {
                        log.warn("[RabbitMQ] STOCK_OUT Order {} → No spot_item found for SKU {} at spot {}",
                                orderId, line.getSku(), pickupSpotCode);
                    }
                });
            }

            order.setStatus("COMPLETED");
            orderRepo.save(order);
            log.info("[RabbitMQ] STOCK_OUT Order {} → COMPLETED", orderId);

            broadcastStockUpdate(spot.getId());
        });
    }

    // ─── REST Endpoints ───

    @PostMapping("/receive")
    public ResponseEntity<PackageResponse> receivePackage(@RequestBody ReceivePackageRequest req) {
        log.info("📦 Receive package request: sku={}, quantity={}, receptionSpotCode={}",
                req.getSku(), req.getQuantity(), req.getReceptionSpotCode());

        // 1. Find item by SKU
        InventoryItemJpaEntity item = itemRepo.findBySku(req.getSku())
                .orElseThrow(() -> {
                    log.error("❌ Item not found for SKU: {}", req.getSku());
                    return new RuntimeException("Item not found: " + req.getSku());
                });
        log.info("✅ Found item: id={}, name={}", item.getId(), item.getName());

        // 2. Find target spot
        String targetSpotCode = findTargetSpot(item.getId());

        // 3. Create incoming_package record
        IncomingPackageJpaEntity pkg = new IncomingPackageJpaEntity();
        pkg.setSku(req.getSku());
        pkg.setItemId(item.getId());
        pkg.setQuantity(req.getQuantity());
        pkg.setStatus("RECEIVED");
        pkg.setReceptionSpotCode(req.getReceptionSpotCode());
        pkg.setTargetSpotCode(targetSpotCode);
        pkg = packageRepo.save(pkg);

        // 4. Publish package.received event via RabbitMQ
        String payload = String.format(
            "{\"packageId\":%d,\"sku\":\"%s\",\"itemId\":%d,\"quantity\":%d,\"receptionSpotCode\":\"%s\",\"targetSpotCode\":\"%s\"}",
            pkg.getId(), pkg.getSku(), pkg.getItemId(), pkg.getQuantity(),
            pkg.getReceptionSpotCode(), pkg.getTargetSpotCode());
        rabbitTemplate.convertAndSend(exchange, "package.dispatched", payload);
        log.info("[RabbitMQ] Published package.dispatched: {}", payload);

        // Broadcast SSE event to frontend
        broadcastPackageUpdate("package-received", pkg);

        return ResponseEntity.ok(toResponse(pkg));
    }

    /**
     * SSE stream endpoint for real-time package events.
     * Frontend connects to /api/packages/stream to receive events:
     * - package-received: new package arrived at warehouse
     * - package-updated: package status changed (IN_TRANSIT, DELIVERED)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPackages() {
        log.info("[SSE-Pkg] New SSE stream connection request");
        return ssePackageAdapter.createEmitter();
    }

    @GetMapping
    public List<PackageResponse> listAll() {
        return packageRepo.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<PackageResponse> getById(@PathVariable Long id) {
        return packageRepo.findById(id)
                .map(p -> ResponseEntity.ok(toResponse(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public List<PackageResponse> getByStatus(@PathVariable String status) {
        return packageRepo.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Finds the target spot for an item and returns the ROOT POINT code (RP-R00-C00 format)
     * so that both UE5 and the frontend can locate it on the grid.
     */
    private String findTargetSpot(Long itemId) {
        List<SpotItemJpaEntity> existing = spotItemRepo.findAll().stream()
                .filter(si -> si.getItemId().equals(itemId))
                .toList();

        String spotCode;
        if (!existing.isEmpty()) {
            Long spotId = existing.get(0).getSpotId();
            spotCode = spotRepo.findById(spotId)
                    .map(SpotJpaEntity::getCode)
                    .orElseGet(() -> getFirstAvailableSpotCode());
        } else {
            spotCode = getFirstAvailableSpotCode();
        }
        return resolveToRootPointCode(spotCode);
    }

    private String getFirstAvailableSpotCode() {
        List<SpotJpaEntity> spots = spotRepo.findAll();
        if (spots.isEmpty()) throw new RuntimeException("No spots configured");
        return spots.get(0).getCode();
    }

    /**
     * Finds a SpotJpaEntity from either a spot code (SP-...) or a root point code (RP-...).
     */
    private SpotJpaEntity findSpotByCode(String code) {
        // If it's a spot code, look it up directly
        if (code.startsWith("SP-")) {
            return spotRepo.findByCode(code)
                    .orElseThrow(() -> new RuntimeException("Spot not found: " + code));
        }
        // If it's a root point code, find any spot linked to that root point
        if (code.startsWith("RP-")) {
            return rootPointRepo.findByCode(code)
                    .flatMap(rp -> spotRepo.findByRootPointId(rp.getId()).stream().findFirst())
                    .orElseThrow(() -> new RuntimeException("No spot found for root point: " + code));
        }
        // Fallback: try as spot code
        return spotRepo.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Spot not found: " + code));
    }

    /**
     * Resolves a SPOT code (e.g. SP-A1-01) to its parent ROOT POINT code (e.g. RP-R02-C01).
     * If the code is already a root point code (starts with "RP-"), returns it as-is.
     */
    private String resolveToRootPointCode(String spotCode) {
        if (spotCode.startsWith("RP-")) {
            return spotCode; // already a root point code
        }
        return spotRepo.findByCode(spotCode)
                .map(SpotJpaEntity::getRootPointId)
                .flatMap(rpId -> rootPointRepo.findById(rpId))
                .map(RootPointJpaEntity::getCode)
                .orElse(spotCode); // fallback: return original code
    }

    /**
     * Broadcast a package event via SSE to all connected frontend clients.
     */
    private void broadcastPackageUpdate(String eventType, IncomingPackageJpaEntity pkg) {
        try {
            String json = objectMapper.writeValueAsString(toResponse(pkg));
            ssePackageAdapter.broadcastPackageEvent(eventType, json);
            log.info("[SSE-Pkg] Broadcast {} for package {}", eventType, pkg.getId());
        } catch (Exception e) {
            log.warn("[SSE-Pkg] Failed to broadcast {}: {}", eventType, e.getMessage());
        }
    }

    /**
     * Broadcast a stock-updated event via SSE so the warehouse UI refreshes item counts.
     * Sends the current items for the given spot.
     */
    private void broadcastStockUpdate(Long spotId) {
        try {
            var items = spotItemRepo.findBySpotId(spotId).stream()
                    .map(si -> {
                        String sku = itemRepo.findById(si.getItemId())
                                .map(InventoryItemJpaEntity::getSku)
                                .orElse("UNKNOWN");
                        return java.util.Map.of(
                                "itemId", si.getItemId(),
                                "sku", sku,
                                "quantityAvailable", si.getQuantityAvailable(),
                                "spotId", spotId
                        );
                    })
                    .toList();
            String json = objectMapper.writeValueAsString(items);
            ssePackageAdapter.broadcastPackageEvent("stock-updated", json);
            log.info("[SSE-Pkg] Broadcast stock-updated for spot {} ({} items)", spotId, items.size());
        } catch (Exception e) {
            log.warn("[SSE-Pkg] Failed to broadcast stock-updated: {}", e.getMessage());
        }
    }

    private PackageResponse toResponse(IncomingPackageJpaEntity pkg) {
        PackageResponse r = new PackageResponse();
        r.setId(pkg.getId());
        r.setSku(pkg.getSku());
        r.setQuantity(pkg.getQuantity());
        r.setStatus(pkg.getStatus());
        r.setReceptionSpotCode(pkg.getReceptionSpotCode());
        r.setTargetSpotCode(pkg.getTargetSpotCode());
        r.setRobotId(pkg.getRobotId());
        r.setCreatedAt(pkg.getCreatedAt());
        return r;
    }
}