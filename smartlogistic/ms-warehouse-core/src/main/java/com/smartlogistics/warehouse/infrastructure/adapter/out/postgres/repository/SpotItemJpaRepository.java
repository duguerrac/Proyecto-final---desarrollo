package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpotItemJpaRepository extends JpaRepository<SpotItemJpaEntity, Long> {
    List<SpotItemJpaEntity> findBySpotId(Long spotId);
    Optional<SpotItemJpaEntity> findBySpotIdAndItemId(Long spotId, Long itemId);
}