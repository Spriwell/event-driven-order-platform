package com.spriwell.orders.kafka;

import com.spriwell.orders.event.InventoryRejectedEvent;
import com.spriwell.orders.event.InventoryReservedEvent;
import com.spriwell.orders.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InventoryEventConsumer {

    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "order-service-group",
            properties = {
                    "spring.json.value.default.type=com.spriwell.orders.event.InventoryReservedEvent"
            }
    )
    public void consumeReserved(InventoryReservedEvent event) {

        log.info("Inventory reserved event received: {}", event);

        orderService.confirmOrder(event.orderId());
    }

    @KafkaListener(
            topics = "inventory.rejected",
            groupId = "order-service-group",
            properties = {
                    "spring.json.value.default.type=com.spriwell.orders.event.InventoryRejectedEvent"
            }
    )
    public void consumeRejected(InventoryRejectedEvent event) {

        log.info("Inventory rejected event received: {}", event);

        orderService.rejectOrder(event.orderId());
    }
}