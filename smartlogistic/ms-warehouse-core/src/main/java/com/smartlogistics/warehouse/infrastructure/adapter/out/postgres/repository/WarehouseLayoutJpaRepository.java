package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.WarehouseLayoutJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WarehouseLayoutJpaRepository extends JpaRepository<WarehouseLayoutJpaEntity, Long> {
    Optional<WarehouseLayoutJpaEntity> findByStatus(String status);
    List<WarehouseLayoutJpaEntity> findAllByOrderByCreatedAtDesc();
}