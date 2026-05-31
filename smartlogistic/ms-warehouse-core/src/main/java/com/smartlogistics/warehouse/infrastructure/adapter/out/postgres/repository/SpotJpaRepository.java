package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotJpaRepository extends JpaRepository<SpotJpaEntity, Long> {
}
