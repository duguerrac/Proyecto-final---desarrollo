package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.InventoryItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryItemJpaRepository extends JpaRepository<InventoryItemJpaEntity, Long> {
    Optional<InventoryItemJpaEntity> findBySku(String sku);
}