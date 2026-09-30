# Orchestration service

Temporal owns the durable order saga; Kafka carries its immutable lifecycle read events. Starting `POST /api/workflows/orders` is idempotent by order ID.

The workflow creates a **pending** payment intent and waits up to 24 hours for an external provider or operator to confirm `COMPLETED`. It never treats intent creation as payment success. It then starts eSIM activation and physical fulfillment in parallel, waits up to seven days for both, and only then starts billing. Terminal dependency failures fail the workflow explicitly.

Events use schema version `1`, Kafka key `orderId`, and deterministic IDs so tracking-service can safely upsert retried deliveries.

## Local deployment

Create the shared network once if no other service has created it, then start the infrastructure and worker:

```bash
docker network create subscription-platform || true
docker compose -f orchestration-service/compose.yaml up -d --build
```

The Compose stack contains one KRaft Kafka broker and one Temporal server backed by PostgreSQL. It is intended for a single-node test environment, not production. No dependency ports are publicly published.

Swagger and health are available on the host loopback at `http://127.0.0.1:8087/swagger-ui.html` and `http://127.0.0.1:8087/actuator/health`.
