package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.LayoutCellJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LayoutCellJpaRepository extends JpaRepository<LayoutCellJpaEntity, Long> {
    List<LayoutCellJpaEntity> findByLayoutIdOrderByRowIndexAscColIndexAsc(Long layoutId);
    void deleteByLayoutId(Long layoutId);
}