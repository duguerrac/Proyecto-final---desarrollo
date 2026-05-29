package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.WarehouseOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseOrderJpaRepository extends JpaRepository<WarehouseOrderJpaEntity, Long> {
    List<WarehouseOrderJpaEntity> findByStatus(String status);
    List<WarehouseOrderJpaEntity> findByRobotId(String robotId);
}