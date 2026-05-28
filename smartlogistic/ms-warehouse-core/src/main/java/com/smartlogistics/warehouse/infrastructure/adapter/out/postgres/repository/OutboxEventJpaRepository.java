package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.OutboxEventJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    List<OutboxEventJpaEntity> findTop10ByStatusOrderByCreatedAtAsc(String status);
}
