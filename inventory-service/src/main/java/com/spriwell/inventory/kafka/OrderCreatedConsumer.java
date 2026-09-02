package com.spriwell.inventory.kafka;

import com.spriwell.inventory.entity.InventoryReservationResult;
import com.spriwell.inventory.entity.RejectedReason;
import com.spriwell.inventory.event.InventoryRejectedEvent;
import com.spriwell.inventory.event.InventoryReservedEvent;
import com.spriwell.inventory.event.OrderCreatedEvent;
import com.spriwell.inventory.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class OrderCreatedConsumer {

    private final InventoryService inventoryService;

    private final InventoryEventProducer inventoryEventProducer;

    public OrderCreatedConsumer(InventoryService inventoryService, InventoryEventProducer inventoryEventProducer) {
        this.inventoryService = inventoryService;
        this.inventoryEventProducer = inventoryEventProducer;
    }

    @KafkaListener(
            topics = "orders.created",
            groupId = "inventory-service-group"
    )
    public void consume(OrderCreatedEvent event) {

        log.info("Received order event: {}", event);

        InventoryReservationResult result = inventoryService.reserveInventory(event);

        switch (result) {

            case RESERVED -> {
                InventoryReservedEvent reservedEvent =
                        new InventoryReservedEvent(
                                UUID.randomUUID(),
                                event.orderId(),
                                event.productId(),
                                event.quantity(),
                                Instant.now()
                        );

                inventoryEventProducer.publishReserved(reservedEvent);
            }

            case INSUFFICIENT_STOCK -> {
                InventoryRejectedEvent rejectedEvent =
                        new InventoryRejectedEvent(
                                UUID.randomUUID(),
                                event.orderId(),
                                event.productId(),
                                event.quantity(),
                                RejectedReason.INSUFFICIENT_STOCK,
                                Instant.now()
                        );

                inventoryEventProducer.publishRejected(rejectedEvent);
            }

            case ALREADY_PROCESSED ->
                log.info(
                        "Ignoring duplicate OrderCreatedEvent: {}",
                        event.eventId()
                );
        }
    }
}