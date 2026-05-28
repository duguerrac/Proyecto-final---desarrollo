package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.application.dto.WarehouseGraphResponse;
import com.smartlogistics.warehouse.application.port.in.GetWarehouseGraphUseCase;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse")
public class WarehouseGraphController {
    private final GetWarehouseGraphUseCase getWarehouseGraph;

    public WarehouseGraphController(GetWarehouseGraphUseCase getWarehouseGraph) {
        this.getWarehouseGraph = getWarehouseGraph;
    }

    @GetMapping("/graph")
    public WarehouseGraphResponse getGraph() {
        return getWarehouseGraph.getGraph();
    }
}
