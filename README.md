# Event-Driven Order Processing Platform

An event-driven order processing system built with **Java 21, Spring Boot, Apache Kafka, and PostgreSQL**.

The platform models an asynchronous order workflow across independently deployable services, with a focus on distributed-system concerns such as **eventual consistency, idempotent message processing, and concurrent inventory updates**.

## Architecture

The system currently consists of two Spring Boot services:

- **Order Service** — exposes the order API and manages the order lifecycle.
- **Inventory Service** — processes inventory reservations asynchronously.

Apache Kafka is used for communication between services.

```text
                         POST /api/orders
                               │
                               ▼
                     ┌─────────────────┐
                     │  Order Service  │
                     └────────┬────────┘
                              │
                       OrderCreatedEvent
                              │
                              ▼
                     ┌─────────────────┐
                     │  Apache Kafka   │
                     └────────┬────────┘
                              │
                              ▼
                   ┌─────────────────────┐
                   │  Inventory Service  │
                   └──────────┬──────────┘
                              │
                 ┌────────────┴─────────────┐
                 │                          │
                 ▼                          ▼
       InventoryReservedEvent     InventoryRejectedEvent
                 │                          │
                 └────────────┬─────────────┘
                              │
                              ▼
                         Apache Kafka
                              │
                              ▼
                       ┌───────────────┐
                       │ Order Service │
                       └───────┬───────┘
                               │
                         ┌─────┴─────┐
                         ▼           ▼
                    CONFIRMED     REJECTED
```

## Order Processing Flow

When an order is submitted:

1. Order Service validates the request.
2. The order is persisted with `PENDING` status.
3. An `OrderCreatedEvent` is published to Kafka.
4. Inventory Service consumes the event and attempts to reserve stock.
5. Inventory Service publishes either:
   - `InventoryReservedEvent`
   - `InventoryRejectedEvent`
6. Order Service consumes the result.
7. The order transitions to `CONFIRMED` or `REJECTED`.

The inventory operation is not part of the original HTTP request, so the workflow is **eventually consistent**.

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot |
| REST API | Spring Web |
| Persistence | Spring Data JPA / Hibernate |
| Messaging | Apache Kafka / Spring Kafka |
| Database | PostgreSQL |
| Build | Maven |
| Infrastructure | Docker |

## Project Structure

```text
event-driven-order-platform/
│
├── order-service/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── inventory-service/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── .gitignore
└── README.md
```

Each service owns its persistence and business logic and communicates with other services through events rather than direct synchronous calls.

## API

### Create an Order

```http
POST /api/orders
Content-Type: application/json
```

Example:

```json
{
  "customerId": 10,
  "productId": 50,
  "quantity": 3
}
```

A successfully accepted order is initially persisted as:

```text
PENDING
```

The final state is determined asynchronously:

```text
PENDING
   │
   ├── InventoryReservedEvent ──► CONFIRMED
   │
   └── InventoryRejectedEvent ──► REJECTED
```

## Event Model

### OrderCreatedEvent

Published by Order Service after an order is created.

```text
eventId
orderId
customerId
productId
quantity
occurredAt
```

### InventoryReservedEvent

Published when Inventory Service successfully reserves the requested stock.

### InventoryRejectedEvent

Published when the reservation cannot be completed, for example because there is insufficient inventory.

## Idempotent Event Processing

Kafka provides at-least-once delivery semantics in common consumer configurations, which means consumers must be prepared to receive the same logical event more than once.

Each order event therefore contains a unique `eventId`.

Inventory Service maintains processed event IDs in a `processed_events` table.

```text
OrderCreatedEvent
       │
       ▼
Is eventId already processed?
       │
   ┌───┴───┐
   │       │
  YES      NO
   │       │
 Ignore    ▼
       Reserve inventory
             │
             ▼
       Store eventId
```

This prevents a duplicate delivery of the same event from reserving inventory multiple times.

## Concurrent Inventory Reservation

Inventory is a shared resource and multiple orders may attempt to reserve the same product concurrently.

A traditional implementation such as:

```text
SELECT inventory
       ↓
Check quantity
       ↓
Subtract quantity
       ↓
UPDATE inventory
```

introduces a read-modify-write race condition.

Instead, inventory reservation is performed using an atomic conditional update:

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity
WHERE product_id = :productId
  AND available_quantity >= :quantity;
```

The affected row count determines the result:

```text
1 row updated  → reservation succeeded
0 rows updated → reservation rejected
```

This keeps the stock validation and modification inside a single database operation and prevents inventory from being oversold through concurrent read-modify-write operations.

The database additionally enforces:

```sql
CHECK (available_quantity >= 0)
```

as an integrity constraint.

## Consistency Model

The services do not participate in a distributed database transaction.

Instead, the system uses asynchronous events and eventual consistency.

For example:

```text
Order Service                 Inventory Service

PENDING
   │
   │ OrderCreatedEvent
   ├──────────────────────────────►
   │
   │                         reserve stock
   │
   │ InventoryReservedEvent
   ◄───────────────────────────────
   │
CONFIRMED
```

This allows the services to remain independently deployable and avoids coupling inventory processing to the latency of the order HTTP request.

## Current Reliability Consideration

The current Order Service persists an order and publishes its `OrderCreatedEvent` as two separate operations:

```text
Save Order → PostgreSQL
Publish Event → Kafka
```

This creates a dual-write failure scenario.

For example:

```text
PostgreSQL commit  ✅
Kafka publication  ❌
```

would leave a persisted order without a corresponding event.

The next reliability improvement is the **Transactional Outbox Pattern**.

The intended design is:

```text
               Database Transaction
        ┌─────────────────────────────┐
        │                             │
        │  Save Order                │
        │      +                     │
        │  Save Outbox Event         │
        │                             │
        └──────────────┬──────────────┘
                       │
                     COMMIT
                       │
                       ▼
                Outbox Publisher
                       │
                       ▼
                     Kafka
```

This allows the business state and the intent to publish an event to be persisted atomically.

## Design Decisions

### Why Kafka instead of synchronous REST between services?

The order workflow does not require inventory processing to complete during the original HTTP request.

Asynchronous communication reduces direct runtime coupling between Order Service and Inventory Service and allows each service to process events independently.

### Why is an order initially PENDING?

Creating the order and reserving inventory are separate operations.

`PENDING` represents an accepted order whose inventory reservation has not yet been resolved.

### Why use an event ID separate from the order ID?

An order is a business entity and can generate multiple events during its lifecycle.

The `orderId` identifies the order.

The `eventId` identifies one specific event and can therefore be used for idempotency.

### Why protect inventory at the database level?

Message ordering alone should not be the only mechanism protecting inventory correctness.

The database operation itself guarantees that stock is only decremented when sufficient quantity exists.

## Current Features

- [x] REST order creation
- [x] Request validation
- [x] PostgreSQL persistence
- [x] Event-driven communication with Kafka
- [x] Independent Order and Inventory services
- [x] Asynchronous inventory processing
- [x] Order state transitions
- [x] Eventual consistency
- [x] Event IDs
- [x] Idempotent inventory event processing
- [x] Concurrent inventory protection
- [x] Database inventory constraint

## Engineering Roadmap

### Reliability

- [ ] Transactional Outbox Pattern
- [ ] Kafka retry strategy
- [ ] Dead Letter Topic (DLT)
- [ ] Consumer error handling

### Testing

- [ ] Unit tests for business rules
- [ ] Repository tests
- [ ] Kafka integration tests
- [ ] PostgreSQL/Kafka integration tests with Testcontainers

### Infrastructure

- [ ] Dockerfiles for services
- [ ] Docker Compose environment
- [ ] Database migrations with Flyway
- [ ] GitHub Actions CI pipeline

### Observability

- [ ] Spring Boot Actuator
- [ ] Application metrics
- [ ] Structured logging
- [ ] Health checks

### API

- [ ] OpenAPI / Swagger documentation
- [ ] Order retrieval endpoint
- [ ] Standardized API error responses

## Project Status

The core asynchronous order-to-inventory workflow is operational.

Current development is focused on strengthening reliability, automated testing, deployment, and observability while preserving clear service boundaries and keeping infrastructure decisions proportional to the system's requirements.
