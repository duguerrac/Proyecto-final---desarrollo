package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RoutePlanJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutePlanJpaRepository extends JpaRepository<RoutePlanJpaEntity, Long> {
}
