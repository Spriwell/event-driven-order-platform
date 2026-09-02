package com.spriwell.inventory.repository;

import com.spriwell.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory,Long> {

    @Modifying
    @Query("""
        UPDATE Inventory i
        SET i.availableQuantity = i.availableQuantity - :quantity
        WHERE i.productId = :productId
          AND i.availableQuantity >= :quantity
    """)
    int reserveStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );
}
