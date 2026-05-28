package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RouteEdgeJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteEdgeJpaRepository extends JpaRepository<RouteEdgeJpaEntity, Long> {
    List<RouteEdgeJpaEntity> findAllByOrderByIdAsc();
}
