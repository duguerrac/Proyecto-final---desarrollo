package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.application.dto.InventorySpotResponse;
import com.smartlogistics.warehouse.application.port.in.GetInventorySpotsUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final GetInventorySpotsUseCase getInventorySpots;

    public InventoryController(GetInventorySpotsUseCase getInventorySpots) {
        this.getInventorySpots = getInventorySpots;
    }

    @GetMapping("/items/{sku}/spots")
    public List<InventorySpotResponse> getBySku(@PathVariable String sku) {
        return getInventorySpots.getBySku(sku);
    }
}
