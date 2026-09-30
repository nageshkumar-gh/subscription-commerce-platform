# Subscription Commerce Platform

A Spring Boot and React reference platform for a subscription-commerce order
lifecycle. Nine backend services own separate bounded contexts and data stores;
Kafka distributes lifecycle events and Temporal coordinates the durable saga.

## Current implementation status

All nine backend services have implementation and test suites in this working
tree. "Implemented" means the code contract exists; it does not mean the whole
stack has been deployed or production-hardened.

| Service | Port | Data/dependency | Current contract and status |
| --- | ---: | --- | --- |
| `customer-service` | 8080 | `customer_db` | Implemented: registration, login, BCrypt passwords, JWT issuance, authenticated `/api/customers/me`, and configured catalogue-admin scope. |
| `product-service` | 8081 | `product_db` | Implemented: public active products/eSIM plans and JWT-protected catalogue administration under `/api/admin/**`. |
| `order-service` | 8082 | `order_db` | Implemented: create/list/get/delete orders, validated status transitions, and device plus first-month total calculation. New orders start in `PENDING_PAYMENT`. |
| `payment-service` | 8083 | `payment_db` | Implemented as a provider-neutral payment-intent boundary: idempotent create, lookup, customer list, and refund request. It does **not** complete payments automatically. |
| `network-service` | 8084 | `network_db` | Implemented: idempotent eSIM activation requests and timed test transitions from queued to active. |
| `fulfillment-service` | 8085 | `fulfillment_db` | Implemented: idempotent fulfilment requests and timed test transitions through delivery. |
| `billing-service` | 8086 | `billing_db` | Implemented: subscriptions wait for activation and fulfilment, then become active; suspend/cancel operations are supported. |
| `orchestration-service` | 8087 | Temporal, Kafka | Implemented: idempotent durable order workflow, dependency polling, lifecycle-event publishing, and workflow status query. |
| `tracking-service` | 8088 | `tracking_db`, Kafka | Implemented: idempotent lifecycle-event projection, chronological order history, summaries, retry, and dead-letter topic. |

The customer `web-ui` uses the real customer, catalogue, order, and workflow
APIs. The `admin-ui` reads order/tracking data and service health. The admin UI
does not yet have identity/RBAC and must not be exposed publicly.

## API contracts

- Customer: `POST /api/auth/register`, `POST /api/auth/login`, and authenticated
  `GET|PUT|DELETE /api/customers/me`.
- Catalogue: public `GET /api/products`, `GET /api/products/{id}`, and
  `GET /api/esim-plans`; admin CRUD uses `/api/admin/products` and
  `/api/admin/esim-plans` with JWT scope `catalog:write`.
- Orders: `/api/orders`; payments: `/api/payments`; activations:
  `/api/activations`; fulfilments: `/api/fulfillments`; subscriptions:
  `/api/subscriptions`.
- Workflow: `POST /api/workflows/orders` and
  `GET /api/workflows/orders/{orderId}`.
- Tracking: `GET /api/tracking/orders` and
  `GET /api/tracking/orders/{orderId}/events`.

Every backend exposes `/v3/api-docs`, `/swagger-ui.html`, and Actuator health.
See [DEPLOYMENT_RUNBOOK.md](DEPLOYMENT_RUNBOOK.md) for private SSH-tunnel access.

## Authentication and catalogue-admin bootstrap

Customer Service signs HS256 JWTs; Product Service validates the same issuer and
secret. `AUTH_JWT_SECRET` is required, must be at least 32 bytes, and must be
identical in both services. Never commit a real value.

Self-registration deliberately creates only ordinary customers. To bootstrap a
catalogue administrator:

1. Register the intended administrator normally through `POST /api/auth/register`.
2. Copy the returned `customer.id` (or read it from `customer_db.customers`).
3. Set `CATALOG_ADMIN_CUSTOMER_IDS` to that ID; use a comma-separated list for
   multiple administrators.
4. Stop Customer Service, recreate it with the updated environment, and start it.
5. Log in again. Registration tokens and tokens issued before step 4 do not gain
   admin rights; a fresh login token contains `scope: catalog:write`.
6. Send that Bearer token to Product Service `/api/admin/**` endpoints.

This is a controlled test bootstrap, not a production IAM design. Production
should use an identity provider, managed roles, audit trails, and secret storage.

## Messaging and workflow behavior

The orchestration stack owns the single-node development Kafka and Temporal
dependencies. It publishes schema-version-1 records to the three-partition
`order-lifecycle-events` topic, keyed by order ID. Tracking Service projects
these events and moves poison records to `order-lifecycle-events.DLT` after
retries.

The workflow intentionally reaches `WAITING_FOR_PAYMENT` after creating a
`PENDING` payment intent. There is no payment-provider callback/operator
confirmation API yet, so an end-to-end workflow will remain there until payment
status becomes `COMPLETED` through a future provider integration. It must not be
reported as a successful payment merely because an intent was created.

## Deployment

Use each Compose file independently from the repository root. Do **not** combine
service Compose files with multiple `-f` arguments: their `build: .` paths are
relative to different service directories and Compose merge semantics can build
the wrong context.

Create the shared network once:

```bash
docker network inspect subscription-platform >/dev/null 2>&1 || \
  docker network create subscription-platform
```

For a complete staged procedure, resource guidance, host ports, start order,
verification, and safe cleanup, follow [DEPLOYMENT_RUNBOOK.md](DEPLOYMENT_RUNBOOK.md).
For centralized logging, follow [OBSERVABILITY.md](OBSERVABILITY.md).

## Local quality checks

Run a backend suite from each service directory:

```bash
mvn test
```

Run each UI's checks from `web-ui` and `admin-ui`:

```bash
npm test
npm run build
npm run lint
```
