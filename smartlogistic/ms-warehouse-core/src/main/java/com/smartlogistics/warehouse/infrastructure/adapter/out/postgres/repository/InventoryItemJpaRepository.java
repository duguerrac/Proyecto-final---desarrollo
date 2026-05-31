package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.InventoryItemJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemJpaRepository extends JpaRepository<InventoryItemJpaEntity, Long> {
    Optional<InventoryItemJpaEntity> findBySku(String sku);
}
