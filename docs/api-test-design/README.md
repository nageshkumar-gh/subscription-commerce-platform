# API Test Design — Subscription Commerce Platform

This folder holds the static test design for the API automation framework: one document per
microservice, plus one for cross-service (end-to-end) flows. Each document lists what the service
does, its endpoints and rules, and the scenarios worth automating, each with an ID for traceability.
There is no test code here.

[api-test-design.html](api-test-design.html) is the same content as a single searchable page (open it
in a browser; filter scenarios by text or priority). It is generated from these Markdown files, so edit
the Markdown and regenerate the page rather than editing the HTML.

## Documents

| # | Document | Service | Port | Store / infra |
| -: | --- | --- | -: | --- |
| 1 | [01-customer-service.md](01-customer-service.md) | Customer, registration, login, JWT | 8080 | MongoDB `customer_db` |
| 2 | [02-product-service.md](02-product-service.md) | Phone catalogue and eSIM plans | 8081 | MongoDB `product_db` |
| 3 | [03-order-service.md](03-order-service.md) | Order records and status state machine | 8082 | MongoDB `order_db` |
| 4 | [04-payment-service.md](04-payment-service.md) | Payment intents, approvals, refunds, recurring charges | 8083 | MongoDB `payment_db` |
| 5 | [05-network-service.md](05-network-service.md) | eSIM activation | 8084 | MongoDB `network_db` |
| 6 | [06-fulfillment-service.md](06-fulfillment-service.md) | Device delivery | 8085 | MongoDB `fulfillment_db` |
| 7 | [07-billing-service.md](07-billing-service.md) | Subscriptions and recurring-billing state | 8086 | MongoDB `billing_db` |
| 8 | [08-orchestration-service.md](08-orchestration-service.md) | Order saga (Temporal), customer storefront API, cancellation | 8087 | Temporal, Kafka |
| 9 | [09-tracking-service.md](09-tracking-service.md) | Kafka lifecycle-event read model | 8088 | MongoDB `tracking_db`, Kafka |
| 10 | [10-invoice-service.md](10-invoice-service.md) | Monthly invoicing batch (Spring Batch) | 8089 | PostgreSQL `invoice` |
| 11 | [11-end-to-end-flows.md](11-end-to-end-flows.md) | Cross-service journeys | — | All |

## How the services fit together

```
web-ui (5173) ──/api/auth, /api/customers──▶ customer-service
              ──/api/products, /api/esim-plans──▶ product-service
              ──/api/me/**──▶ orchestration-service ──▶ order, product, payment, network,
                                                         fulfillment, billing, tracking

admin-ui (5174) ──(nginx auth_request: token needs scope orders:operate)──▶
              order, payment, network, fulfillment, billing, tracking, orchestration, invoice

orchestration-service ──Kafka "order-lifecycle-events"──▶ tracking-service
invoice-service ──▶ billing-service (due subscriptions, record billed period)
                ──▶ payment-service (recurring charges)
billing-service ──polls──▶ payment, network, fulfillment
```

Order saga as the workflow drives it:

**Payment → device delivery → eSIM activation → billing approval → COMPLETED**

Each stage waits for an operator to approve or reject it, through the step service's
`/approve` and `/reject` endpoints, which the admin console calls.

## Authentication model (what the framework must handle)

| Layer | Who enforces it | What is required |
| --- | --- | --- |
| Customer profile (`/api/customers/me`) | customer-service | Bearer JWT issued by customer-service |
| Catalogue admin (`/api/admin/**`) | product-service | JWT with scope `catalog:write` |
| Storefront (`/api/me/**`) | orchestration-service | Bearer JWT; the subject is the customer |
| Operations APIs (orders, payments, activations, fulfilments, subscriptions, tracking, workflows, invoicing) | **admin-ui nginx only** | JWT with scope `orders:operate`, checked through `GET /api/auth/verify?scope=orders:operate` |
| Order, payment, network, fulfillment, billing, tracking, invoice and `/api/workflows/**` | **none at service level** | Open when called directly on their ports |

Elevated scopes come only from configuration (`CATALOG_ADMIN_CUSTOMER_IDS`, `AGENT_CUSTOMER_IDS`)
and are granted only on **login**, never on registration. The framework therefore needs three
bootstrap identities:

1. **Customer**: registers, then logs in. No scope.
2. **Catalogue admin**: a registered ID listed in `CATALOG_ADMIN_CUSTOMER_IDS`; its login token carries `catalog:write`.
3. **Operations agent**: a registered ID listed in `AGENT_CUSTOMER_IDS`; its login token carries `orders:operate`.

All services that validate tokens share `AUTH_JWT_SECRET` (HS256, at least 32 bytes, issuer `customer-service`).

## Recommended test layers

| Layer | Target | Purpose |
| --- | --- | --- |
| **Smoke** | Every service's `/actuator/health` and `/v3/api-docs` | Environment is up and the contract is published |
| **Contract** | Each service's OpenAPI document | Request/response schema, required fields, enum values, status codes; detect breaking changes |
| **Component (service-level functional)** | One service on its own port, real database | CRUD, validation, state machines, idempotency, error bodies |
| **Security** | customer, product, orchestration `/api/me`, and both edge proxies | 401/403 paths, scope checks, data isolation between customers, CORS |
| **Integration** | Pairs (billing↔payment/network/fulfillment, invoice↔billing/payment, orchestration→Kafka→tracking) | Calls and events between services |
| **End-to-end** | Through web-ui and admin-ui proxies | Full customer and operator journeys (see doc 11) |
| **Resilience** (optional) | Stop a dependency mid-flow | Retries, 503 on cancellation, run restarts, DLT |

## Common checks for every service

These apply to every service document and are not repeated in each one.

| ID | Check | Expected |
| --- | --- | --- |
| COM-01 | `GET /actuator/health` | 200, `status` = `UP`; components (MongoDB / DB / Kafka) are UP where the service shows details |
| COM-02 | `GET /actuator/health/liveness` and `/readiness` (services with probes enabled) | 200 UP |
| COM-03 | `GET /v3/api-docs` | 200; valid OpenAPI 3; every documented endpoint in this folder is present |
| COM-04 | `GET /swagger-ui.html` | Redirects to or serves Swagger UI |
| COM-05 | Unknown path under `/api` | 404 (or 401/403 on secured services) |
| COM-06 | Wrong HTTP method on a known path | 405 (or 401/403 on secured services) |
| COM-07 | Malformed JSON body | 400 |
| COM-08 | Wrong `Content-Type` on a POST | 415 |
| COM-09 | CORS preflight from an allowed origin | `Access-Control-Allow-Origin` echoes the origin; allowed methods match the service |
| COM-10 | CORS preflight from a non-allowed origin | No `Access-Control-Allow-Origin` header (request rejected) |
| COM-11 | Error body shape | Matches the service's documented error format (see each doc) |
| COM-12 | Response time | Single-entity reads under an agreed SLA (for example p95 < 500 ms) in the test environment |

## Error body formats (differ per service, so assert accordingly)

| Service | Shape |
| --- | --- |
| customer | `timestamp, status, error, message, path` (first field error's message only) |
| product, network, fulfillment, billing | `timestamp, status, error, message` (validation message is `field: message`) |
| order, payment | `timestamp, status, error, code, message, path, fieldErrors{}` with codes such as `VALIDATION_FAILED`, `ORDER_STATE_CONFLICT`, `PAYMENT_CONFLICT` |
| orchestration, invoice | `status, message` |

## Test data strategy

- **Unique data per test**: emails like `qa+<runId>-<n>@example.test`, SKUs and plan codes with a run suffix, and order IDs taken from responses.
- **Seed data**: product-service seeds 2 phones (`IPHONE-18-PRO-512`, `IPHONE-18-PRO-MAX-1TB`) and 2 plans (`LIMITED-2GB-DAY`, `UNLIMITED`) only when its collections are empty. Do not rely on them being unchanged; create your own catalogue items for write tests.
- **Configuration-driven behaviour** that the test environment should set:
  - `NETWORK_AUTO_ADVANCE` / `FULFILLMENT_AUTO_ADVANCE` = `false` (default) so tests drive steps through approve/reject. Run a separate suite with `true` to cover the simulated provider.
  - `payment.simulated-decline-customers`: one known customer ID whose recurring charges always fail.
  - `invoice.async-runs`: the default (`true`) returns run IDs immediately; tests poll run status.
- **Clean-up**: delete only what the test created (customer `DELETE /me`, product/plan admin DELETE, order DELETE while `PENDING_PAYMENT` or `CANCELLED`). Many records (payments, activations, invoices, events) have no delete API, so isolate by unique IDs rather than by wiping.
- **Async waits**: billing reconciles every 5 s, the workflow polls every 10 s (5 min after 15 min), the invoice schedule checks every 60 s. Use polling with timeouts, never fixed sleeps.

## Scenario ID convention

`<SVC>-<AREA>-<NN>` where SVC is CUS, PRD, ORD, PAY, NET, FUL, BIL, ORC, TRK, INV or E2E. Priority:
**P1** must pass for a release, **P2** important regression, **P3** edge case or hardening.
