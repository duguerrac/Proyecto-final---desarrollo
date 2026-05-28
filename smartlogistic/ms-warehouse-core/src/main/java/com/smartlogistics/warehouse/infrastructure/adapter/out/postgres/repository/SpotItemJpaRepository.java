package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotItemJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpotItemJpaRepository extends JpaRepository<SpotItemJpaEntity, Long> {
    @Query(value = """
            SELECT s.id AS spot_id, s.code AS spot_code, i.id AS item_id, i.sku,
                   (si.quantity_available - si.quantity_reserved) AS quantity_available
            FROM spot_item si
            JOIN spot s ON s.id = si.spot_id
            JOIN inventory_item i ON i.id = si.item_id
            WHERE i.sku = :sku
            ORDER BY s.code
            """, nativeQuery = true)
    List<Object[]> findSpotsBySku(@Param("sku") String sku);

    @Query(value = """
            SELECT COUNT(*)
            FROM order_item oi
            WHERE oi.order_id = :orderId
              AND EXISTS (
                  SELECT 1 FROM spot_item si
                  WHERE si.item_id = oi.item_id
                    AND (si.quantity_available - si.quantity_reserved) >= oi.requested_quantity
              )
            """, nativeQuery = true)
    int countAvailableItemsForOrder(@Param("orderId") Long orderId);

    @Query(value = """
            SELECT COUNT(*)
            FROM order_item oi
            WHERE oi.order_id = :orderId
              AND EXISTS (
                  SELECT 1 FROM spot_item si
                  WHERE si.item_id = oi.item_id
                    AND si.quantity_reserved >= oi.requested_quantity
              )
            """, nativeQuery = true)
    int countReservedItemsForOrder(@Param("orderId") Long orderId);

    @Modifying
    @Query(value = """
            WITH picked AS (
                SELECT DISTINCT ON (si.item_id) si.id, oi.requested_quantity
                FROM spot_item si
                JOIN order_item oi ON oi.item_id = si.item_id
                WHERE oi.order_id = :orderId
                  AND (si.quantity_available - si.quantity_reserved) >= oi.requested_quantity
                ORDER BY si.item_id, (si.quantity_available - si.quantity_reserved) DESC
            )
            UPDATE spot_item si
            SET quantity_reserved = si.quantity_reserved + picked.requested_quantity
            FROM picked
            WHERE si.id = picked.id
            """, nativeQuery = true)
    void reserveStockForOrder(@Param("orderId") Long orderId);

    @Modifying
    @Query(value = """
            WITH picked AS (
                SELECT DISTINCT ON (si.item_id) si.id, oi.requested_quantity
                FROM spot_item si
                JOIN order_item oi ON oi.item_id = si.item_id
                WHERE oi.order_id = :orderId AND si.quantity_reserved >= oi.requested_quantity
                ORDER BY si.item_id, si.quantity_reserved DESC
            )
            UPDATE spot_item si
            SET quantity_available = si.quantity_available - picked.requested_quantity,
                quantity_reserved = si.quantity_reserved - picked.requested_quantity
            FROM picked
            WHERE si.id = picked.id
            """, nativeQuery = true)
    void decrementStockForOrder(@Param("orderId") Long orderId);
}
