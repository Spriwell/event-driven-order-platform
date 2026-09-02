package com.spriwell.orders.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        Long orderId,
        Long productId,
        Integer quantity,
        Instant occurredAt
) {
}
