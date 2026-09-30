# Tracking service

Tracking-service consumes versioned order lifecycle events from Kafka and stores a query-oriented MongoDB read model. Event IDs are document IDs, making redelivery idempotent. Kafka records must be keyed by `orderId`, preserving ordering within an order. Poison records are retried twice and then sent to `order-lifecycle-events.DLT` rather than blocking the partition forever.

Read APIs:

- `GET /api/tracking/orders` — current summary for every tracked order.
- `GET /api/tracking/orders/{orderId}/events` — chronological audit trail.

```bash
docker compose -f tracking-service/compose.yaml up -d --build
```

Kafka must already be running from the orchestration Compose stack. Swagger and health bind only to host loopback on port `8088`.
