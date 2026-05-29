package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.application.service.LayoutService;
import com.smartlogistics.warehouse.domain.model.*;
import com.smartlogistics.warehouse.infrastructure.adapter.in.rest.dto.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/layouts")
public class LayoutController {

    private final LayoutService layoutService;

    public LayoutController(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @PostMapping
    public ResponseEntity<LayoutResponse> createLayout(@RequestBody CreateLayoutRequest request) {
        List<LayoutCell> cells = request.cells() != null
                ? request.cells().stream()
                    .map(c -> new LayoutCell(null, null, c.rowIndex(), c.colIndex(), CellType.valueOf(c.cellType())))
                    .toList()
                : List.of();

        WarehouseLayout layout = layoutService.createLayout(
                request.name(), request.rows(), request.cols(), request.cellSize(), cells);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(layout));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LayoutResponse> updateLayout(
            @PathVariable Long id, @RequestBody UpdateLayoutRequest request) {
        List<LayoutCell> cells = request.cells() != null
                ? request.cells().stream()
                    .map(c -> new LayoutCell(null, null, c.rowIndex(), c.colIndex(), CellType.valueOf(c.cellType())))
                    .toList()
                : null;

        WarehouseLayout layout = layoutService.updateLayout(id, request.name(), cells);
        return ResponseEntity.ok(toResponse(layout));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<LayoutResponse> activateLayout(@PathVariable Long id) {
        WarehouseLayout layout = layoutService.activateLayout(id);
        return ResponseEntity.ok(toResponse(layout));
    }

    @GetMapping
    public ResponseEntity<List<LayoutResponse>> listLayouts() {
        List<LayoutResponse> responses = layoutService.listLayouts().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LayoutResponse> getLayout(@PathVariable Long id) {
        WarehouseLayout layout = layoutService.getLayout(id);
        return ResponseEntity.ok(toResponse(layout));
    }

    @GetMapping("/active")
    public ResponseEntity<LayoutResponse> getActiveLayout() {
        WarehouseLayout layout = layoutService.getActiveLayout();
        return ResponseEntity.ok(toResponse(layout));
    }

    private LayoutResponse toResponse(WarehouseLayout layout) {
        List<CellDTO> cells = layout.cells() != null
                ? layout.cells().stream()
                    .map(c -> new CellDTO(c.rowIndex(), c.colIndex(), c.cellType().name()))
                    .toList()
                : List.of();
        return new LayoutResponse(
                layout.id(), layout.name(), layout.rowsCount(), layout.colsCount(),
                layout.cellSize(), layout.status().name(), layout.createdAt(),
                layout.activatedAt(), cells, List.of());
    }
}