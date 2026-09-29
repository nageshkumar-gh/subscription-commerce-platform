## Subscription Commerce Platform

A Spring Boot microservices project demonstrating customer management,
products, orders, payments, eSIM activation, fulfilment, Kafka event streaming,
and Temporal workflow orchestration.

### Services

| Service | Port | MongoDB database | Main endpoints |
| --- | ---: | --- | --- |
| customer-service | 8080 | customer_db | `/api/customers` |
| product-service | 8081 | product_db | `/api/products`, `/api/esim-plans` |
| order-service | 8082 | order_db | `/api/orders` |
| payment-service | 8083 | payment_db | `/api/payments` |
| network-service | 8084 | network_db | `/api/activations` |
| fulfillment-service | 8085 | fulfillment_db | `/api/fulfillments` |
| billing-service | 8086 | billing_db | `/api/subscriptions` |
| orchestration-service | 8087 | Temporal | `/api/workflows/orders` |
| tracking-service | 8088 | tracking_db | `/api/tracking/orders/{orderId}/events` |
| admin-ui | 5174 | — | Agent order-tracking dashboard |

### Incremental Docker setup

Each independently deployable component owns its Compose file. Start only the
slice you are developing instead of running the complete distributed system on
one laptop. Each data-owning service also owns its MongoDB container and volume.

MongoDB is pinned to `7.0-jammy` because MongoDB 8's TCMalloc implementation
cannot start on Linux kernels 6.19 through 7.0.13. This is particularly relevant
when Docker Desktop supplies the Linux VM kernel.

The Customer slice creates the shared `subscription-platform` Docker network.
The Web UI joins that network and resolves backends at request time, so services
that have not been started yet do not prevent the UI from loading.

```bash
docker compose -f customer-service/compose.yaml up --build -d
docker compose -f web-ui/compose.yaml up --build -d
docker compose -f customer-service/compose.yaml ps
docker compose -f web-ui/compose.yaml ps
```

Open the customer UI at `http://localhost:5173`. Customer Service is available
directly at `http://localhost:8080`; its Swagger UI is at
`http://localhost:8080/swagger-ui.html`.

Follow logs independently:

```bash
docker compose -f customer-service/compose.yaml logs -f
docker compose -f web-ui/compose.yaml logs -f
```

For centralized EC2 container logs and infrastructure monitoring, see
[`OBSERVABILITY.md`](OBSERVABILITY.md). It configures selective Datadog log
collection without committing API keys.

Stop this slice without deleting customer data:

```bash
docker compose -f web-ui/compose.yaml down
docker compose -f customer-service/compose.yaml down
```

Do not add `--volumes` unless you intentionally want to erase that service's
database. Product, Order, Payment, and the workflow infrastructure will be added
as separate deployments and tested one slice at a time.

| Service | MongoDB container | Host port | Database | Volume |
| --- | --- | ---: | --- | --- |
| customer-service | `customer-mongodb` | — (Docker network only) | `customer_db` | `customer-mongodb-data` |
| product-service | `product-mongodb` | 27018 | `product_db` | `product-mongodb-data` |
| order-service | `order-mongodb` | 27019 | `order_db` | `order-mongodb-data` |
| payment-service | `payment-mongodb` | 27020 | `payment_db` | `payment-mongodb-data` |
| network-service | `network-mongodb` | 27021 | `network_db` | `network-mongodb-data` |
| fulfillment-service | `fulfillment-mongodb` | 27022 | `fulfillment_db` | `fulfillment-mongodb-data` |
| billing-service | `billing-mongodb` | 27023 | `billing_db` | `billing-mongodb-data` |
| tracking-service | `tracking-mongodb` | 27024 | `tracking_db` | `tracking-mongodb-data` |

### Kafka and Temporal flow

1. `web-ui` creates the order, then starts an order workflow through `orchestration-service`.
2. Temporal durably retries payment, eSIM activation, fulfilment, and billing activities.
3. The workflow waits until the eSIM is `ACTIVE` and delivery is `DELIVERED` before starting billing.
4. Lifecycle milestones are published to the three-partition `order-lifecycle-events` Kafka topic, keyed by order ID to preserve per-order ordering.
5. `tracking-service` consumes those events into its own MongoDB read model; `admin-ui` displays the latest event alongside live service statuses.

Kafka is the event distribution channel, while Temporal is the workflow state
and retry engine. Kafka events are not used as the source of truth for workflow
progress.

### OpenAPI documentation

Every service publishes an OpenAPI 3 document and an interactive Swagger UI:

| Service | OpenAPI JSON | Swagger UI |
| --- | --- | --- |
| customer-service | `http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| product-service | `http://localhost:8081/v3/api-docs` | `http://localhost:8081/swagger-ui.html` |
| order-service | `http://localhost:8082/v3/api-docs` | `http://localhost:8082/swagger-ui.html` |
| payment-service | `http://localhost:8083/v3/api-docs` | `http://localhost:8083/swagger-ui.html` |
| network-service | `http://localhost:8084/v3/api-docs` | `http://localhost:8084/swagger-ui.html` |
| fulfillment-service | `http://localhost:8085/v3/api-docs` | `http://localhost:8085/swagger-ui.html` |
| billing-service | `http://localhost:8086/v3/api-docs` | `http://localhost:8086/swagger-ui.html` |
| orchestration-service | `http://localhost:8087/v3/api-docs` | `http://localhost:8087/swagger-ui.html` |
| tracking-service | `http://localhost:8088/v3/api-docs` | `http://localhost:8088/swagger-ui.html` |

The documents are generated from the controllers, request models, validation constraints, response models, and OpenAPI annotations, keeping the specification aligned with the implementation.

### Customer authentication

Customer Service provides password authentication and signed JWT bearer tokens:

- `POST /api/auth/register` creates a customer and stores only a BCrypt password hash.
- `POST /api/auth/login` verifies the password and returns a one-hour access token.
- `GET /api/customers/me`, `PUT /api/customers/me`, and
  `DELETE /api/customers/me` require `Authorization: Bearer <token>`.

The Docker Compose secret is intentionally a local-development value. Set a
strong `AUTH_JWT_SECRET` from a secret manager in shared or production
environments; never commit a production secret. Existing customer records that
predate authentication have no password hash and cannot log in until migrated
or re-registered.
