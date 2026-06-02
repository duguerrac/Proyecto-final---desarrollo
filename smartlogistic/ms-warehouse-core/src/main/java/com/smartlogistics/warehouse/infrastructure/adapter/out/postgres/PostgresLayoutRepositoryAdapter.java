package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres;

import com.smartlogistics.warehouse.application.port.out.LayoutRepositoryPort;
import com.smartlogistics.warehouse.domain.model.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.*;
import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresLayoutRepositoryAdapter implements LayoutRepositoryPort {

    private final WarehouseLayoutJpaRepository layoutRepository;
    private final LayoutCellJpaRepository cellRepository;
    private final RootPointJpaRepository rootPointRepository;
    private final RouteEdgeJpaRepository routeEdgeRepository;
    private final SpotJpaRepository spotRepository;

    public PostgresLayoutRepositoryAdapter(
            WarehouseLayoutJpaRepository layoutRepository,
            LayoutCellJpaRepository cellRepository,
            RootPointJpaRepository rootPointRepository,
            RouteEdgeJpaRepository routeEdgeRepository,
            SpotJpaRepository spotRepository) {
        this.layoutRepository = layoutRepository;
        this.cellRepository = cellRepository;
        this.rootPointRepository = rootPointRepository;
        this.routeEdgeRepository = routeEdgeRepository;
        this.spotRepository = spotRepository;
    }

    @Override
    @Transactional
    public WarehouseLayout save(WarehouseLayout layout) {
        WarehouseLayoutJpaEntity layoutEntity = new WarehouseLayoutJpaEntity();
        if (layout.id() != null) {
            layoutEntity = layoutRepository.findById(layout.id()).orElse(new WarehouseLayoutJpaEntity());
        }
        layoutEntity.setName(layout.name());
        layoutEntity.setRowsCount(layout.rowsCount());
        layoutEntity.setColsCount(layout.colsCount());
        layoutEntity.setCellSize(layout.cellSize());
        layoutEntity.setStatus(layout.status().name());
        layoutEntity.setCreatedAt(layout.createdAt() != null ? layout.createdAt() : LocalDateTime.now());
        layoutEntity.setActivatedAt(layout.activatedAt());

        WarehouseLayoutJpaEntity saved = layoutRepository.saveAndFlush(layoutEntity);

        // Save cells
        if (layout.id() != null) {
            cellRepository.deleteByLayoutId(saved.getId());
        }
        List<LayoutCell> savedCells = new ArrayList<>();
        if (layout.cells() != null) {
            for (LayoutCell cell : layout.cells()) {
                LayoutCellJpaEntity cellEntity = new LayoutCellJpaEntity();
                cellEntity.setLayoutId(saved.getId());
                cellEntity.setRowIndex(cell.rowIndex());
                cellEntity.setColIndex(cell.colIndex());
                cellEntity.setCellType(cell.cellType().name());
                LayoutCellJpaEntity savedCell = cellRepository.save(cellEntity);
                savedCells.add(new LayoutCell(savedCell.getId(), savedCell.getLayoutId(),
                        savedCell.getRowIndex(), savedCell.getColIndex(), CellType.valueOf(savedCell.getCellType())));
            }
        }

        return mapToDomain(saved, savedCells);
    }

    @Override
    public Optional<WarehouseLayout> findById(Long id) {
        return layoutRepository.findById(id).map(e -> {
            List<LayoutCell> cells = findCells(e.getId());
            return mapToDomain(e, cells);
        });
    }

    @Override
    public List<WarehouseLayout> findAll() {
        return layoutRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> mapToDomain(e, findCells(e.getId())))
                .toList();
    }

    @Override
    public Optional<WarehouseLayout> findActive() {
        return layoutRepository.findByStatus("ACTIVE").map(e -> {
            List<LayoutCell> cells = findCells(e.getId());
            return mapToDomain(e, cells);
        });
    }

    @Override
    @Transactional
    public void archiveActive() {
        layoutRepository.findByStatus("ACTIVE").ifPresent(e -> {
            e.setStatus("ARCHIVED");
            layoutRepository.save(e);
        });
    }

    @Override
    @Transactional
    public void deleteAllRootPoints() {
        spotRepository.deleteAllSpotItems();
        spotRepository.deleteAllInBatch();
        routeEdgeRepository.deleteAllInBatch();
        rootPointRepository.deleteAllInBatch();
    }

    @Override
    @Transactional
    public void deleteAllRouteEdges() {
        routeEdgeRepository.deleteAllInBatch();
    }

    @Override
    @Transactional
    public void saveRootPoint(RootPoint rootPoint) {
        RootPointJpaEntity entity = new RootPointJpaEntity();
        entity.setId(rootPoint.id());
        entity.setCode(rootPoint.code());
        entity.setX(rootPoint.x());
        entity.setY(rootPoint.y());
        entity.setZLevel(rootPoint.zLevel());
        entity.setType(rootPoint.type());
        entity.setBlocked(rootPoint.blocked());
        rootPointRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void saveRouteEdge(RouteEdge edge) {
        RouteEdgeJpaEntity entity = new RouteEdgeJpaEntity();
        entity.setSourceId(edge.sourceId());
        entity.setTargetId(edge.targetId());
        entity.setDistance(edge.distance());
        entity.setBidirectional(edge.bidirectional());
        entity.setWeight(edge.weight());
        routeEdgeRepository.save(entity);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, LocalDateTime activatedAt) {
        layoutRepository.findById(id).ifPresent(e -> {
            e.setStatus(status);
            e.setActivatedAt(activatedAt);
            layoutRepository.save(e);
        });
    }

    @Override
    @Transactional
    public void flush() {
        rootPointRepository.flush();
        spotRepository.flush();
        routeEdgeRepository.flush();
    }

    @Override
    @Transactional
    public void saveSpotCode(String code, String aisle, String section, int level, Long rootPointId, BigDecimal x, BigDecimal y, BigDecimal z) {
        SpotJpaEntity entity = new SpotJpaEntity();
        entity.setCode(code);
        entity.setAisle(aisle);
        entity.setSection(section);
        entity.setLevel(level);
        entity.setRootPointId(rootPointId);
        entity.setX(x);
        entity.setY(y);
        entity.setZ(z);
        spotRepository.save(entity);
    }

    private List<LayoutCell> findCells(Long layoutId) {
        return cellRepository.findByLayoutIdOrderByRowIndexAscColIndexAsc(layoutId).stream()
                .map(c -> new LayoutCell(c.getId(), c.getLayoutId(), c.getRowIndex(), c.getColIndex(), CellType.valueOf(c.getCellType())))
                .toList();
    }

    private WarehouseLayout mapToDomain(WarehouseLayoutJpaEntity e, List<LayoutCell> cells) {
        return new WarehouseLayout(
                e.getId(), e.getName(), e.getRowsCount(), e.getColsCount(),
                e.getCellSize(), LayoutStatus.valueOf(e.getStatus()),
                e.getCreatedAt(), e.getActivatedAt(), cells);
    }
}