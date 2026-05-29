package com.smartlogistics.warehouse.application.port.out;

import com.smartlogistics.warehouse.domain.model.*;
import java.util.List;
import java.util.Optional;

public interface LayoutRepositoryPort {
    WarehouseLayout save(WarehouseLayout layout);
    Optional<WarehouseLayout> findById(Long id);
    List<WarehouseLayout> findAll();
    Optional<WarehouseLayout> findActive();
    void archiveActive();
    void deleteAllRootPoints();
    void deleteAllRouteEdges();
    void saveRootPoint(RootPoint rootPoint);
    void saveRouteEdge(RouteEdge edge);
    void saveSpotCode(String code, String aisle, String section, int level, Long rootPointId, java.math.BigDecimal x, java.math.BigDecimal y, java.math.BigDecimal z);
    void updateStatus(Long id, String status, java.time.LocalDateTime activatedAt);

    void flush();
}
