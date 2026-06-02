package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.RootPointJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RootPointJpaRepository extends JpaRepository<RootPointJpaEntity, Long> {
    List<RootPointJpaEntity> findAllByOrderByIdAsc();
    List<RootPointJpaEntity> findByIdInOrderByIdAsc(List<Long> ids);
    Optional<RootPointJpaEntity> findByCode(String code);

    @Query(value = """
            SELECT DISTINCT rp.*
            FROM order_item oi
            JOIN spot_item si ON si.item_id = oi.item_id AND si.quantity_reserved >= oi.requested_quantity
            JOIN spot s ON s.id = si.spot_id
            JOIN root_point rp ON rp.id = s.root_point_id
            WHERE oi.order_id = :orderId
            ORDER BY rp.code
            """, nativeQuery = true)
    List<RootPointJpaEntity> findPickingRootPointsForOrder(@Param("orderId") Long orderId);
}
