package com.smartlogistics.warehouse.application.service;

import com.smartlogistics.warehouse.application.port.out.LayoutRepositoryPort;
import com.smartlogistics.warehouse.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LayoutService {

    private static final Logger log = LoggerFactory.getLogger(LayoutService.class);
    private final LayoutRepositoryPort repository;
    private final LayoutProcessor processor;

    public LayoutService(LayoutRepositoryPort repository, LayoutProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    public WarehouseLayout createLayout(String name, int rows, int cols, BigDecimal cellSize, List<LayoutCell> cells) {
        log.info("Creating layout '{}' ({}x{})", name, rows, cols);
        WarehouseLayout layout = new WarehouseLayout(
                null, name, rows, cols, cellSize, LayoutStatus.DRAFT,
                LocalDateTime.now(), null, cells);
        return repository.save(layout);
    }

    public WarehouseLayout updateLayout(Long id, String name, List<LayoutCell> cells) {
        WarehouseLayout existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Layout not found: " + id));
        if (existing.status() == LayoutStatus.ACTIVE) {
            throw new IllegalStateException("Cannot modify an active layout. Archive it first.");
        }
        WarehouseLayout updated = new WarehouseLayout(
                existing.id(), name != null ? name : existing.name(),
                existing.rowsCount(), existing.colsCount(), existing.cellSize(),
                existing.status(), existing.createdAt(), existing.activatedAt(), cells);
        return repository.save(updated);
    }

    public WarehouseLayout activateLayout(Long id) {
        WarehouseLayout layout = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Layout not found: " + id));
        if (layout.status() == LayoutStatus.ACTIVE) {
            throw new IllegalStateException("Layout is already active");
        }

        // Process the grid into root_points, edges, spots
        processor.activateLayout(layout);

        // Update layout status to ACTIVE (use updateStatus to avoid re-inserting cells)
        LocalDateTime now = LocalDateTime.now();
        repository.updateStatus(layout.id(), LayoutStatus.ACTIVE.name(), now);
        return repository.findById(layout.id())
                .orElseThrow(() -> new IllegalStateException("Layout not found after activation"));
    }

    public List<WarehouseLayout> listLayouts() {
        return repository.findAll();
    }

    public WarehouseLayout getLayout(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Layout not found: " + id));
    }

    public WarehouseLayout getActiveLayout() {
        return repository.findActive()
                .orElseThrow(() -> new IllegalStateException("No active layout found"));
    }
}