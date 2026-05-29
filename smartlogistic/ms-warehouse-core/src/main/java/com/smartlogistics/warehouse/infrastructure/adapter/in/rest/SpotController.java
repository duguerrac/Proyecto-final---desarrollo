package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/warehouse/spots")
public class SpotController {

    private final SpotJpaRepository spotRepo;
    private final SpotItemJpaRepository spotItemRepo;
    private final InventoryItemJpaRepository itemRepo;

    public SpotController(SpotJpaRepository spotRepo,
                          SpotItemJpaRepository spotItemRepo,
                          InventoryItemJpaRepository itemRepo) {
        this.spotRepo = spotRepo;
        this.spotItemRepo = spotItemRepo;
        this.itemRepo = itemRepo;
    }

    /** GET /api/v1/warehouse/spots — list all spots with items */
    @GetMapping
    public List<SpotDTO> listSpots() {
        List<SpotJpaEntity> spots = spotRepo.findAll();
        return spots.stream().map(this::toSpotDTO).toList();
    }

    /** GET /api/v1/warehouse/spots/{code} — single spot by code */
    @GetMapping("/{code}")
    public ResponseEntity<SpotDTO> getByCode(@PathVariable String code) {
        return spotRepo.findByCode(code)
                .map(s -> ResponseEntity.ok(toSpotDTO(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/v1/warehouse/spots/{code}/items — items in a spot */
    @GetMapping("/{code}/items")
    public ResponseEntity<List<SpotItemDTO>> getItems(@PathVariable String code) {
        return spotRepo.findByCode(code)
                .map(spot -> {
                    List<SpotItemDTO> items = buildItemDTOs(spot.getId());
                    return ResponseEntity.ok(items);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/v1/warehouse/spots/{code}/items — add or update item stock */
    @PostMapping("/{code}/items")
    public ResponseEntity<SpotItemDTO> addItem(
            @PathVariable String code,
            @Valid @RequestBody AddSpotItemRequest req) {

        Optional<SpotJpaEntity> spotOpt = spotRepo.findByCode(code);
        if (spotOpt.isEmpty()) return ResponseEntity.notFound().build();

        Optional<InventoryItemJpaEntity> itemOpt = itemRepo.findById(req.itemId());
        if (itemOpt.isEmpty()) return ResponseEntity.badRequest().build();

        SpotJpaEntity spot = spotOpt.get();
        InventoryItemJpaEntity item = itemOpt.get();

        // Upsert: if item already in spot, update quantity
        SpotItemJpaEntity spotItem = spotItemRepo
                .findBySpotIdAndItemId(spot.getId(), item.getId())
                .orElseGet(() -> {
                    SpotItemJpaEntity si = new SpotItemJpaEntity();
                    si.setSpotId(spot.getId());
                    si.setItemId(item.getId());
                    si.setQuantityReserved(0);
                    return si;
                });

        spotItem.setQuantityAvailable(req.quantityAvailable());
        spotItem = spotItemRepo.save(spotItem);

        return ResponseEntity.ok(new SpotItemDTO(
                item.getId(), item.getName(), item.getSku(),
                spotItem.getQuantityAvailable()));
    }

    // ── helpers ──────────────────────────────────────────────

    private SpotDTO toSpotDTO(SpotJpaEntity s) {
        return new SpotDTO(
                s.getId(), s.getCode(), s.getAisle(),
                s.getSection(), s.getX(), s.getY(),
                buildItemDTOs(s.getId()));
    }

    private List<SpotItemDTO> buildItemDTOs(Long spotId) {
        List<SpotItemJpaEntity> items = spotItemRepo.findBySpotId(spotId);
        List<SpotItemDTO> dtos = new ArrayList<>();
        for (SpotItemJpaEntity si : items) {
            itemRepo.findById(si.getItemId()).ifPresent(item ->
                    dtos.add(new SpotItemDTO(
                            item.getId(), item.getName(), item.getSku(),
                            si.getQuantityAvailable())));
        }
        return dtos;
    }
}