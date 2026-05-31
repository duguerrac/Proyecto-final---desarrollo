package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.PackageResponse;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.ReceivePackageRequest;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.IncomingPackageJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.InventoryItemJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotItemJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotJpaEntity;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.IncomingPackageJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.InventoryItemJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.SpotItemJpaRepository;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.SpotJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public PackageController(IncomingPackageJpaRepository packageRepo,
                             InventoryItemJpaRepository itemRepo,
                             SpotJpaRepository spotRepo,
                             SpotItemJpaRepository spotItemRepo,
                             RabbitTemplate rabbitTemplate,
                             @Value("${rabbitmq.exchange:logistics.exchange}") String exchange) {
        this.packageRepo = packageRepo;
        this.itemRepo = itemRepo;
        this.spotRepo = spotRepo;
        this.spotItemRepo = spotItemRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    // ─── RabbitMQ Listeners (replacing NATS subscriptions) ───

    @RabbitListener(queues = "${rabbitmq.queue.package-taken:package.taken}")
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
            });
        } catch (Exception e) {
            log.error("Error processing package.taken", e);
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.package-delivered:package.delivered}")
    public void onPackageDelivered(String json) {
        log.info("[RabbitMQ] package.delivered received: {}", json);
        try {
            var tree = objectMapper.readTree(json);
            Long packageId = tree.get("packageId").asLong();

            packageRepo.findById(packageId).ifPresent(pkg -> {
                SpotJpaEntity spot = spotRepo.findByCode(pkg.getTargetSpotCode())
                        .orElseThrow(() -> new RuntimeException("Spot not found: " + pkg.getTargetSpotCode()));

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
                log.info("[RabbitMQ] Package {} → DELIVERED. Added {}x SKU {} to spot {}",
                        packageId, pkg.getQuantity(), pkg.getSku(), pkg.getTargetSpotCode());
            });
        } catch (Exception e) {
            log.error("Error processing package.delivered", e);
        }
    }

    // ─── REST Endpoints ───

    @PostMapping("/receive")
    public ResponseEntity<PackageResponse> receivePackage(@RequestBody ReceivePackageRequest req) {
        // 1. Find item by SKU
        InventoryItemJpaEntity item = itemRepo.findBySku(req.getSku())
                .orElseThrow(() -> new RuntimeException("Item not found: " + req.getSku()));

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
        rabbitTemplate.convertAndSend(exchange, "package.received", payload);
        log.info("[RabbitMQ] Published package.received: {}", payload);

        return ResponseEntity.ok(toResponse(pkg));
    }

    @GetMapping
    public List<PackageResponse> listAll() {
        return packageRepo.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
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

    private String findTargetSpot(Long itemId) {
        List<SpotItemJpaEntity> existing = spotItemRepo.findAll().stream()
                .filter(si -> si.getItemId().equals(itemId))
                .toList();

        if (!existing.isEmpty()) {
            Long spotId = existing.get(0).getSpotId();
            return spotRepo.findById(spotId)
                    .map(SpotJpaEntity::getCode)
                    .orElseGet(() -> getFirstAvailableSpot());
        }
        return getFirstAvailableSpot();
    }

    private String getFirstAvailableSpot() {
        List<SpotJpaEntity> spots = spotRepo.findAll();
        if (spots.isEmpty()) throw new RuntimeException("No spots configured");
        return spots.get(0).getCode();
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