package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RobotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotJpaRepository extends JpaRepository<RobotJpaEntity, String> {

    List<RobotJpaEntity> findByAvailableTrue();
}