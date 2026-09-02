package com.spriwell.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "inventory")
@Getter
public class Inventory {

    @Id
    private Long productId;

    private Integer availableQuantity;

    protected Inventory() {}

    public Inventory(Long productId, Integer availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    public boolean hasEnoughStock(Integer requestedQuantity) {
        return availableQuantity >= requestedQuantity;
    }

    public void reserve(Integer quantity) {
        availableQuantity -= quantity;
    }
}
