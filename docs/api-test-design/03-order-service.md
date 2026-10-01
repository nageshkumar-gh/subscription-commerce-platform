# 03 — Order Service

**Port** 8082 · **Store** MongoDB `order_db.orders` · **Role** system of record for orders and their status.

It stores a price snapshot, calculates the checkout total and guards the order status state machine.
Orders are created by orchestration-service (storefront) and moved forward by the workflow; operators
reach it through the admin proxy.

## Endpoints

| Method | Path | Auth (service) | Success | Purpose |
| --- | --- | --- | --- | --- |
| POST | `/api/orders` | None | 201 | Create an order in `PENDING_PAYMENT` |
| GET | `/api/orders` | None | 200 | All orders |
| GET | `/api/orders?customerId=` | None | 200 | A customer's orders, newest first |
| GET | `/api/orders/{id}` | None | 200 / 404 | One order |
| PATCH | `/api/orders/{id}/status` | None | 200 | Change status (state machine) |
| DELETE | `/api/orders/{id}` | None | 204 | Delete a pending or cancelled order |

## Data rules (create)

| Field | Rule |
| --- | --- |
| customerId, productId, planId | Required, ≤ 100 |
| productName, planName | Required, ≤ 200 |
| storage | Required, ≤ 50 |
| devicePrice, monthlyPrice | Required, > 0 |

**Server-set fields**: `id`, `status` = `PENDING_PAYMENT`, `total` = devicePrice + monthlyPrice,
`createdAt`, `updatedAt` (both equal on create).

## State machine

Happy path: `PENDING_PAYMENT → PAID → DISPATCHED → DELIVERED → ACTIVATED → COMPLETED`

| From | Allowed to | Not allowed |
| --- | --- | --- |
| PENDING_PAYMENT | PAID, CANCELLED, FAILED | DISPATCHED, DELIVERED, ACTIVATED, COMPLETED (payment must come first) |
| PAID … ACTIVATED | Any **later** happy-path status (skipping allowed), CANCELLED, FAILED | Earlier statuses |
| FAILED | CANCELLED only | Everything else |
| COMPLETED, CANCELLED | Nothing (terminal) | Everything |
| Any | Same status again | Returns 200 unchanged (no-op, `updatedAt` unchanged) |

## Functional scenarios

### Create and read

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORD-CRT-01 | P1 | Create with valid data | 201; status `PENDING_PAYMENT`; `total` = device + monthly |
| ORD-CRT-02 | P1 | Decimal total accuracy (899.00 + 14.99) | `total` = 913.99 exactly |
| ORD-CRT-03 | P2 | Client sends `status`, `total`, `id` | Ignored; server values used |
| ORD-CRT-04 | P2 | Each required field missing or blank | 400 `VALIDATION_FAILED`, `fieldErrors` names the field |
| ORD-CRT-05 | P2 | Price 0 or negative | 400 |
| ORD-CRT-06 | P3 | Length boundaries (100 / 200 / 50 and +1) | Boundary accepted, +1 rejected |
| ORD-RD-01 | P1 | Get by ID | 200; matches create response |
| ORD-RD-02 | P1 | Unknown ID | 404, code `ORDER_NOT_FOUND` |
| ORD-RD-03 | P1 | Filter by customerId | Only that customer's orders, sorted by `createdAt` descending |
| ORD-RD-04 | P2 | Filter by customerId with surrounding spaces | Trimmed; same result |
| ORD-RD-05 | P2 | Unknown customerId | 200 empty list |
| ORD-RD-06 | P3 | Blank customerId | Treated as no filter (all orders) |

### Status transitions

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORD-STS-01 | P1 | Walk the full happy path step by step | Each PATCH 200; `updatedAt` increases |
| ORD-STS-02 | P1 | PENDING_PAYMENT → DISPATCHED (skip payment) | 409 `ORDER_STATE_CONFLICT` "Cannot transition order from PENDING_PAYMENT to DISPATCHED" |
| ORD-STS-03 | P1 | PAID → DELIVERED (skip allowed after payment) | 200 |
| ORD-STS-04 | P1 | DELIVERED → PAID (backwards) | 409 |
| ORD-STS-05 | P1 | Any non-terminal → CANCELLED | 200 |
| ORD-STS-06 | P1 | Any non-terminal → FAILED | 200 |
| ORD-STS-07 | P1 | FAILED → CANCELLED | 200 |
| ORD-STS-08 | P2 | FAILED → PAID | 409 |
| ORD-STS-09 | P1 | COMPLETED → anything else; CANCELLED → anything else | 409 |
| ORD-STS-10 | P2 | Same status again (for example PAID → PAID) | 200, unchanged |
| ORD-STS-11 | P2 | Invalid enum value / missing status | 400 |
| ORD-STS-12 | P2 | Unknown order ID | 404 |
| ORD-STS-13 | P3 | Data-driven: all 8 × 8 from/to pairs against the table above | Matches the table exactly |

### Delete

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| ORD-DEL-01 | P1 | Delete a `PENDING_PAYMENT` order | 204; GET gives 404 |
| ORD-DEL-02 | P1 | Delete a `CANCELLED` order | 204 |
| ORD-DEL-03 | P1 | Delete PAID / DELIVERED / COMPLETED / FAILED | 409 "Only pending or cancelled orders can be deleted" |
| ORD-DEL-04 | P2 | Delete an unknown ID | 404 |

## Security and robustness

| ID | P | Scenario | Expected / note |
| --- | --- | --- | --- |
| ORD-SEC-01 | P1 | Direct call on port 8082 without a token | **Succeeds**: the service has no authentication. Confirm the port is not exposed publicly (deployment check) |
| ORD-SEC-02 | P1 | Same call through admin-ui proxy without an `orders:operate` token | 401 / 403 at nginx |
| ORD-SEC-03 | P2 | Create directly with arbitrary prices | Accepted (no catalogue check here); storefront price integrity is enforced by orchestration (ORC-STO-03) |
| ORD-SEC-04 | P3 | Concurrent PATCHes to different statuses | Final status is a valid one; note there is no optimistic locking |

## Integration notes

- The workflow moves orders: PAID on payment COMPLETED, DISPATCHED / DELIVERED from fulfilment,
  ACTIVATED on eSIM ACTIVE, COMPLETED on billing ACTIVE, FAILED on any step rejection, CANCELLED on cancellation.
- A refused transition from the workflow is logged and ignored, never failing the saga.
