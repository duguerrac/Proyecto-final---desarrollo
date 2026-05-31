package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RouteStepJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStepJpaRepository extends JpaRepository<RouteStepJpaEntity, Long> {
    List<RouteStepJpaEntity> findByRoutePlanIdOrderBySequenceAsc(Long routePlanId);
}
