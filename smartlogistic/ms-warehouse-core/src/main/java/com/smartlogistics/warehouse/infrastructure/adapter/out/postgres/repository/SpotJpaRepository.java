package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SpotJpaRepository extends JpaRepository<SpotJpaEntity, Long> {

    @Modifying
    @Query(value = "DELETE FROM spot_item", nativeQuery = true)
    void deleteAllSpotItems();

    Optional<SpotJpaEntity> findByCode(String code);
    List<SpotJpaEntity> findByAisle(String aisle);
}
