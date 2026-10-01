# 08 — Orchestration Service

**Port** 8087 · **Infra** Temporal (task queue `order-lifecycle`), Kafka (topic `order-lifecycle-events`) ·
**Calls** order, product, payment, network, fulfillment, billing, tracking.

It has three responsibilities, each tested separately:

1. **Storefront API** (`/api/me/**`): the signed-in customer's orders. JWT required.
2. **Workflow API** (`/api/workflows/orders/**`): start, query and cancel an order saga. Internal (no service auth; agent token at the admin proxy).
3. **The saga itself**: payment → delivery → eSIM activation → billing, with compensation on cancel and Kafka events at every step.

## Endpoints

| Method | Path | Auth | Success | Purpose |
| --- | --- | --- | --- | --- |
| POST | `/api/me/orders` | Bearer | 201 | Place an order (`productId`, `planId`) at catalogue prices |
| POST | `/api/me/orders/{orderId}/checkout` | Bearer | 202 | Start the saga; repeat-safe |
| GET | `/api/me/orders` | Bearer | 200 | My orders, newest first |
| GET | `/api/me/orders/{orderId}` | Bearer | 200 | Order + payment, delivery, activation, billing records + events |
| GET | `/api/me/orders/{orderId}/events` | Bearer | 200 | My order's lifecycle history |
| POST | `/api/me/orders/{orderId}/cancel` | Bearer | 202 | Cancel before the subscription is active (`reason` ≤ 300) |
| GET | `/api/me/subscriptions` | Bearer | 200 | My ACTIVE subscriptions |
| POST | `/api/workflows/orders` | None | 202 STARTED / 200 existing state | Start the saga for an order |
| GET | `/api/workflows/orders/{orderId}` | None | 200 / 404 | Current workflow state |
| POST | `/api/workflows/orders/{orderId}/cancel` | None | 202 | Operator cancel (`reason` ≤ 500) |

Error body for all of these: `{status, message}`.

## Workflow states (as returned by the status query)

`STARTED → WAITING_FOR_PAYMENT → AWAITING_DELIVERY → AWAITING_ACTIVATION → AWAITING_BILLING_APPROVAL → COMPLETED`,
plus `CANCELLING`, `CANCELLED`, `FAILED`, and `NOT_STARTED` (shown as 404).

| Stage | Waits for | Fails on | Timeout | Order synced to |
| --- | --- | --- | --- | --- |
| Payment | payment `COMPLETED` | `FAILED`, `REFUNDED` | 24 h | PAID |
| Delivery | fulfilment `DELIVERED` | `FAILED` | 7 days | DISPATCHED, DELIVERED |
| eSIM activation | activation `ACTIVE` | `FAILED` | 3 days | ACTIVATED |
| Billing | subscription `ACTIVE` | `REJECTED` | 7 days | COMPLETED |

Polling: every 10 s for the first 15 min of a stage, then every 5 min. A cancel signal wakes it immediately.
Dependency outages (5xx) are retried for up to 10 minutes; 4xx responses fail the step without retry.

## Functional scenarios

### Storefront: place order

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORC-STO-01 | P1 | Place an order for an active product and active plan | 201; order with `customerId` = token subject, `PENDING_PAYMENT`, product/plan names and prices from the catalogue |
| ORC-STO-02 | P1 | `total` | = catalogue price + plan monthly price |
| ORC-STO-03 | P1 | Request includes `customerId`, `devicePrice`, `total` | Ignored; token subject and catalogue prices used |
| ORC-STO-04 | P1 | Inactive or unknown product | 400 "That phone is not available" |
| ORC-STO-05 | P1 | Inactive or unknown plan | 400 "That plan is not available" |
| ORC-STO-06 | P2 | Missing / blank productId or planId, > 100 chars | 400 |

### Storefront: read, checkout, cancel

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORC-STO-10 | P1 | List my orders | Only mine, newest first |
| ORC-STO-11 | P1 | Order details right after placing | `order` present; `payment`, `delivery`, `activation`, `billing` null; `events` empty |
| ORC-STO-12 | P1 | Checkout | 202 `{orderId, status: "STARTED"}` |
| ORC-STO-13 | P1 | Checkout again | 202 with the current workflow state (for example `WAITING_FOR_PAYMENT`); no second payment |
| ORC-STO-14 | P1 | Details after checkout | `payment` present and `PENDING`; `events` include STARTED, WAITING_FOR_PAYMENT, PAYMENT PENDING |
| ORC-STO-15 | P1 | Cancel my order before billing is ACTIVE | 202 `CANCELLING` (running) or `CANCELLED` (not checked out) |
| ORC-STO-16 | P1 | Cancel after the subscription is ACTIVE | 409 "Your subscription is already active; contact support to cancel it" |
| ORC-STO-17 | P2 | Cancel without reason / reason > 300 | 400 |
| ORC-STO-18 | P2 | Checkout a cancelled order | 409 "Order is cancelled" |
| ORC-STO-19 | P1 | My subscriptions | Only my ACTIVE subscriptions |
| ORC-STO-20 | P2 | tracking-service down | Details and events still 200, with `events` empty |

### Storefront: security and isolation

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORC-SEC-01 | P1 | Any `/api/me/**` call without a token | 401 |
| ORC-SEC-02 | P1 | Customer B reads, checks out, cancels or lists events of customer A's order | 404 "Order not found" (never 403, so IDs cannot be probed) |
| ORC-SEC-03 | P1 | Customer B's order list and subscriptions | Never contain A's data |
| ORC-SEC-04 | P2 | Expired / wrong-secret / wrong-issuer token | 401 |
| ORC-SEC-05 | P1 | `/api/workflows/**` directly without a token | Succeeds (internal; no service auth). Must be private; admin proxy requires `orders:operate`, web-ui proxy does not route it |

### Workflow API

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORC-WF-01 | P1 | Start with a valid request for a new order | 202 `{workflowId: "order-<orderId>", status: "STARTED"}` |
| ORC-WF-02 | P1 | Start again (running) | 200 with current state |
| ORC-WF-03 | P1 | Start again after COMPLETED / FAILED / CANCELLED | 200 with that final state; the workflow is not restarted |
| ORC-WF-04 | P1 | Start for a CANCELLED order | 409 "Order is cancelled" |
| ORC-WF-05 | P2 | Validation: blank IDs or names, total / monthlyAmount < 0.01 | 400 |
| ORC-WF-06 | P1 | Status of a running workflow | 200 with the stage state |
| ORC-WF-07 | P1 | Status of an order never started | 404 "Workflow not found" |
| ORC-WF-08 | P1 | Cancel a running workflow | 202 `CANCELLING`; eventually status `CANCELLED` |
| ORC-WF-09 | P1 | Cancel an order whose workflow never started | 202 `CANCELLED` immediately; order `CANCELLED`; one CANCELLED event published |
| ORC-WF-10 | P1 | Cancel a FAILED workflow's order | 202 `CANCELLED`; compensated directly |
| ORC-WF-11 | P1 | Cancel a COMPLETED order | 409 "Order is COMPLETED and can no longer be cancelled; cancel its subscription instead" |
| ORC-WF-12 | P2 | Cancel while CANCELLING, or an already-CANCELLED order | 409 |
| ORC-WF-13 | P2 | Cancel an unknown order | 404 "Order not found" |
| ORC-WF-14 | P2 | Cancel without reason / > 500 chars | 400 |
| ORC-WF-15 | P3 | A dependency is down during direct compensation | 503 "Cancellation could not reach every service; please retry…" |

### Saga behaviour (drive steps through payment / fulfilment / network / billing APIs)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORC-SAGA-01 | P1 | Checkout | Payment intent created with key `order-<orderId>`, amount = order total, EUR; state `WAITING_FOR_PAYMENT` |
| ORC-SAGA-02 | P1 | Approve payment | Within ~10 s: order `PAID`; state `AWAITING_DELIVERY`; fulfilment record created |
| ORC-SAGA-03 | P1 | Approve fulfilment to DISPATCHED, then DELIVERED | Order `DISPATCHED`, then `DELIVERED`; state `AWAITING_ACTIVATION`; activation record created only now |
| ORC-SAGA-04 | P1 | Approve activation to ACTIVE | Order `ACTIVATED`; state `AWAITING_BILLING_APPROVAL`; subscription created with the matching activation and fulfilment IDs |
| ORC-SAGA-05 | P1 | Approve billing | Order `COMPLETED`; workflow `COMPLETED` |
| ORC-SAGA-06 | P1 | Reject payment with a reason | Workflow `FAILED`; order `FAILED`; a PAYMENT FAILED event carries the reason |
| ORC-SAGA-07 | P1 | Reject fulfilment / activation / billing | Same pattern for each stage (FULFILLMENT FAILED, ESIM_ACTIVATION FAILED, BILLING REJECTED) |
| ORC-SAGA-08 | P2 | Activation is not requested before delivery | No activation record exists while fulfilment is not DELIVERED |
| ORC-SAGA-09 | P2 | Stage timeout (use a test Temporal time-skip environment) | FAILED with message "<Stage> did not complete within N hours" |
| ORC-SAGA-10 | P3 | Dependency down for under 10 min, then back | Saga continues; not failed |

### Cancellation compensation matrix

Cancel at each stage and check every downstream record:

| ID | Cancel when | Payment | Fulfilment | Activation | Billing | Order |
| --- | --- | --- | --- | --- | --- | --- |
| ORC-CMP-01 | Waiting for payment | PENDING → FAILED ("payment voided") | none | none | none | CANCELLED |
| ORC-CMP-02 | Awaiting delivery (paid) | COMPLETED → REFUND_PENDING ("refund requested") | → FAILED ("delivery stopped") | none | none | CANCELLED |
| ORC-CMP-03 | Awaiting activation (delivered) | REFUND_PENDING | DELIVERED kept ("arrange a return") | → FAILED ("eSIM activation stopped") | none | CANCELLED |
| ORC-CMP-04 | Awaiting billing approval | REFUND_PENDING | DELIVERED kept | ACTIVE kept ("must be deactivated manually") | → CANCELLED ("billing cancelled") | CANCELLED |

Also check (ORC-CMP-05, P1): events end with `ORDER_WORKFLOW CANCELLING`, then `ORDER_WORKFLOW CANCELLED`,
with detail = reason plus the outcome summary in brackets. Customer cancels have the reason prefixed `Customer: `.

### Kafka events (verify with a test consumer or through tracking-service)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORC-EVT-01 | P1 | Every event | `schemaVersion` 1; key = orderId; fields eventId, orderId, customerId, eventType, status, occurredAt present |
| ORC-EVT-02 | P1 | Event types | `ORDER_WORKFLOW`, `PAYMENT`, `FULFILLMENT`, `ESIM_ACTIVATION`, `BILLING` only |
| ORC-EVT-03 | P2 | Event IDs | `order-<orderId>-<seq>-<type>-<status>`, sequence strictly increasing; cancel-without-workflow uses `order-<id>-cancel-<epochMillis>` |
| ORC-EVT-04 | P2 | Ordering per order | All events for one order land on one partition in sequence order |
| ORC-EVT-05 | P2 | Each status change is published once | No duplicate status events for a stage while polling |
