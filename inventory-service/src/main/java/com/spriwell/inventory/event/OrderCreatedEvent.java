package com.spriwell.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        Long customerId,
        Long productId,
        Integer quantity,
        Instant occurredAt
) {
}

