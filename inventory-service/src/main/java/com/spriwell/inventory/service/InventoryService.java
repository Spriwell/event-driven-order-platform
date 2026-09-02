package com.spriwell.inventory.service;

import com.spriwell.inventory.entity.Inventory;
import com.spriwell.inventory.entity.InventoryReservationResult;
import com.spriwell.inventory.entity.ProcessedEvent;
import com.spriwell.inventory.event.OrderCreatedEvent;
import com.spriwell.inventory.repository.InventoryRepository;
import com.spriwell.inventory.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProcessedEventRepository processedEventRepository;

    public InventoryService(InventoryRepository inventoryRepository, ProcessedEventRepository processedEventRepository) {
        this.inventoryRepository = inventoryRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public InventoryReservationResult reserveInventory(
            OrderCreatedEvent event
    ) {

        if (processedEventRepository.existsById(event.eventId())) {
            return InventoryReservationResult.ALREADY_PROCESSED;
        }

        int updatedRows = inventoryRepository.reserveStock(
                event.productId(),
                event.quantity()
        );

        processedEventRepository.save(
                new ProcessedEvent(event.eventId())
        );

        if (updatedRows == 1) {
            return InventoryReservationResult.RESERVED;
        }

        return InventoryReservationResult.INSUFFICIENT_STOCK;
    }
}
