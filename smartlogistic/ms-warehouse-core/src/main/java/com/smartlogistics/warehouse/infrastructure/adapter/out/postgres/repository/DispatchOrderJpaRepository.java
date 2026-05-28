package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.DispatchOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchOrderJpaRepository extends JpaRepository<DispatchOrderJpaEntity, Long> {
}
