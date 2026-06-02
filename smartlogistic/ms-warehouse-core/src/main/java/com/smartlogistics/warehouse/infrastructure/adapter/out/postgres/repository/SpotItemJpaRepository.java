package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.SpotItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SpotItemJpaRepository extends JpaRepository<SpotItemJpaEntity, Long> {
    List<SpotItemJpaEntity> findBySpotId(Long spotId);
    Optional<SpotItemJpaEntity> findBySpotIdAndItemId(Long spotId, Long itemId);

    @Query(value = """
        SELECT sp.id, sp.code, si.item_id, i.sku, si.quantity_available
        FROM spot_item si
        JOIN spot sp ON si.spot_id = sp.id
        JOIN inventory_item i ON si.item_id = i.id
        WHERE i.sku = :sku
    """, nativeQuery = true)
    List<Object[]> findSpotsBySku(@Param("sku") String sku);

    @Query("""
        SELECT COALESCE(SUM(si.quantityAvailable), 0)
        FROM SpotItemJpaEntity si
        WHERE si.itemId IN (
            SELECT oi.itemId FROM OrderItemJpaEntity oi WHERE oi.orderId = :orderId
        )
    """)
    int countAvailableItemsForOrder(@Param("orderId") Long orderId);

    @Query("""
        SELECT COALESCE(SUM(si.quantityReserved), 0)
        FROM SpotItemJpaEntity si
        WHERE si.itemId IN (
            SELECT oi.itemId FROM OrderItemJpaEntity oi WHERE oi.orderId = :orderId
        )
    """)
    int countReservedItemsForOrder(@Param("orderId") Long orderId);

    @Modifying
    @Query(value = """
        UPDATE spot_item si
        SET quantity_available = quantity_available - oi.requested_quantity,
            quantity_reserved = quantity_reserved - oi.requested_quantity
        FROM order_item oi
        WHERE oi.order_id = :orderId
          AND si.item_id = oi.item_id
          AND si.quantity_available >= oi.requested_quantity
    """, nativeQuery = true)
    void decrementStockForOrder(@Param("orderId") Long orderId);

    @Modifying
    @Query(value = """
        UPDATE spot_item si
        SET quantity_reserved = quantity_reserved + oi.requested_quantity
        FROM order_item oi
        WHERE oi.order_id = :orderId
          AND si.item_id = oi.item_id
          AND si.quantity_available >= oi.requested_quantity
    """, nativeQuery = true)
    void reserveStockForOrder(@Param("orderId") Long orderId);
}
