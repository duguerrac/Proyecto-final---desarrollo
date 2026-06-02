package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.IncomingPackageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncomingPackageJpaRepository extends JpaRepository<IncomingPackageJpaEntity, Long> {
    List<IncomingPackageJpaEntity> findByStatus(String status);
    List<IncomingPackageJpaEntity> findByStatusIn(List<String> statuses);
}