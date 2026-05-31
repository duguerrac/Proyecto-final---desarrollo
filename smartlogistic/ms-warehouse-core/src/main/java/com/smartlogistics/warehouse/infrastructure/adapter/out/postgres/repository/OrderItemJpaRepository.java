package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.repository;

import com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity.OrderItemJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemJpaEntity, Long> {
    List<OrderItemJpaEntity> findByOrderIdOrderByIdAsc(Long orderId);

    @Query(value = """
            SELECT i.id, i.sku, oi.requested_quantity, i.fragile
            FROM order_item oi
            JOIN inventory_item i ON i.id = oi.item_id
            WHERE oi.order_id = :orderId
            ORDER BY oi.id
            """, nativeQuery = true)
    List<Object[]> findDomainItemsByOrderId(@Param("orderId") Long orderId);
}
