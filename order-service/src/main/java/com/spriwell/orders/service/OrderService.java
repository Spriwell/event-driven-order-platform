package com.spriwell.orders.service;

import com.spriwell.orders.dto.CreateOrderRequest;
import com.spriwell.orders.entity.Order;
import com.spriwell.orders.event.OrderCreatedEvent;
import com.spriwell.orders.kafka.OrderEventProducer;
import com.spriwell.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderEventProducer orderEventProducer;

    public OrderService(OrderRepository orderRepository, OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
    }

    public Order createNewOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.getCustomerId(),
                request.getProductId(),
                request.getQuantity()
        );

        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.getProductId(),
                savedOrder.getQuantity(),
                Instant.now()
        );

        orderEventProducer.publishOrderCreated(event);

        return savedOrder;
    }

    public Order confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId
                ));

        order.confirm();

        return orderRepository.save(order);
    }

    public Order rejectOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId
                ));

        order.reject();

        return orderRepository.save(order);
    }
}
