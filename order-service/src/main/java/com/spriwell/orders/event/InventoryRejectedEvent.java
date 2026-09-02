package com.spriwell.orders.event;

import com.spriwell.orders.entity.RejectedReason;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.UUID;

public record InventoryRejectedEvent(
        UUID eventId,
        Long orderId,
        Long productId,
        Integer requestedQuantity,
        @Enumerated(EnumType.STRING)
        RejectedReason rejectedReason,
        Instant occurredAt
) {
}
