# 07 — Billing Service (subscriptions)

**Port** 8086 · **Store** MongoDB `billing_db.subscriptions` · **Depends on** payment (8083), network (8084), fulfillment (8085).

It owns the recurring subscription for an order. It tracks delivery and activation progress, lets an
operator start billing only when payment, delivery and eSIM activation are complete, and exposes due
subscriptions and billed periods to invoice-service.

## Endpoints

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| POST | `/api/subscriptions` | 201 (also for an idempotent repeat) | Create or return the subscription for an order |
| GET | `/api/subscriptions` | 200 | All subscriptions |
| GET | `/api/subscriptions?orderId=` | 200 / 404 | The order's subscription (takes precedence over other params) |
| GET | `/api/subscriptions?customerId=[&status=]` | 200 | A customer's subscriptions, newest first, optionally by status |
| POST | `/api/subscriptions/{orderId}/approve` | 200 | Start billing (READY_FOR_BILLING → ACTIVE) |
| POST | `/api/subscriptions/{orderId}/reject` | 200 | Reject before billing starts (reason required) |
| GET | `/api/subscriptions/due?asOf=YYYY-MM-DD&page=&size=` | 200 | ACTIVE subscriptions due on or before `asOf` |
| PUT | `/api/subscriptions/{orderId}/billing-day` | 200 | Change invoicing day (1–28) |
| POST | `/api/subscriptions/{orderId}/billing-periods` | 200 | Record an invoiced period and advance `nextBillingAt` |
| POST | `/api/subscriptions/{orderId}/suspend` | 200 | Suspend |
| POST | `/api/subscriptions/{orderId}/cancel` | 200 | Cancel (idempotent) |

Note: action paths take the **orderId**, not the subscription ID. No service-level authentication.

## Data rules

**Create**: `orderId`, `customerId`, `planName`, `activationId`, `fulfillmentId` (not blank),
`monthlyAmount` > 0. On create the service checks that `activationId` and `fulfillmentId` match the
records network and fulfillment hold for the order (skipped if a dependency is unreachable).

**Server fields**: `id`, `status`, `createdAt`, `billingStartedAt`, `nextBillingAt`, `billingDay`,
`statusChangedAt`, `statusReason`.

## Status model

```
WAITING_FOR_FULFILLMENT ─(delivery DELIVERED)→ WAITING_FOR_ACTIVATION ─(eSIM ACTIVE)→ READY_FOR_BILLING
      (reconciler every 5 s; the device must be delivered before activation counts)

READY_FOR_BILLING ─approve→ ACTIVE
WAITING_* / READY_FOR_BILLING ─reject→ REJECTED
any (not CANCELLED) ─suspend→ SUSPENDED
any ─cancel→ CANCELLED
```

**On approve**: requires payment `COMPLETED`, fulfilment `DELIVERED` and activation `ACTIVE` (IDs must match).
Sets `billingStartedAt` = now, first invoice date = today (UTC) + 1 month, `nextBillingAt` = start of that
day (UTC), `billingDay` = min(day of month, 28).

## Functional scenarios

### Create and read

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| BIL-CRT-01 | P1 | Create with IDs matching the real activation and fulfilment | 201; `WAITING_FOR_FULFILLMENT` |
| BIL-CRT-02 | P1 | Repeat with identical data | 201; same `id` |
| BIL-CRT-03 | P1 | Same orderId, any field different | 409 "A subscription already exists for this order with different details" |
| BIL-CRT-04 | P1 | `activationId` not matching network's record | 409 "The activation ID does not match the dependency for this order" |
| BIL-CRT-05 | P1 | `fulfillmentId` not matching | 409 "The fulfillment ID does not match…" |
| BIL-CRT-06 | P2 | Network / fulfillment unreachable at create time | 201 (validation skipped); record a risk |
| BIL-CRT-07 | P2 | Validation: blank fields, amount 0 | 400 |
| BIL-RD-01 | P1 | By orderId / unknown orderId | 200 / 404 "Subscription not found for order …" |
| BIL-RD-02 | P2 | By customerId, and by customerId + `status=ACTIVE` | Filtered, newest first |
| BIL-RD-03 | P3 | Invalid `status` value | 400 |

### Reconciliation (asynchronous, poll up to ~10 s)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| BIL-REC-01 | P1 | Fulfilment approved to DELIVERED | Subscription moves to `WAITING_FOR_ACTIVATION` |
| BIL-REC-02 | P1 | Then activation approved to ACTIVE | Moves to `READY_FOR_BILLING` |
| BIL-REC-03 | P2 | Activation ACTIVE while delivery not DELIVERED | Stays `WAITING_FOR_FULFILLMENT` |
| BIL-REC-04 | P2 | Dependency service down | Status unchanged; no error; recovers when it returns |
| BIL-REC-05 | P2 | READY_FOR_BILLING never moves to ACTIVE by itself | Only operator approval starts billing |

### Approve and reject

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| BIL-APR-01 | P1 | Approve READY_FOR_BILLING with payment COMPLETED | 200; `ACTIVE`; `nextBillingAt` = today + 1 month at 00:00 UTC; `billingDay` = min(day, 28) |
| BIL-APR-02 | P1 | Approve when payment is PENDING | 409 "Billing cannot start: payment is not COMPLETED" |
| BIL-APR-03 | P2 | Approve when several dependencies are incomplete | 409 listing every missing condition |
| BIL-APR-04 | P1 | Approve while WAITING_* | 409 "Billing can only be approved when the subscription is READY_FOR_BILLING; it is …" |
| BIL-APR-05 | P1 | Approve an ACTIVE subscription | 200 unchanged (idempotent) |
| BIL-APR-06 | P3 | Approve on the 29th–31st of a month | `billingDay` = 28 |
| BIL-REJ-01 | P1 | Reject WAITING_* / READY_FOR_BILLING with a reason | 200; `REJECTED`; `nextBillingAt` null |
| BIL-REJ-02 | P1 | Reject without a reason / no body | 400 |
| BIL-REJ-03 | P1 | Reject ACTIVE / SUSPENDED / CANCELLED | 409 "Billing can only be rejected before it starts…" |
| BIL-REJ-04 | P2 | Reject an already REJECTED subscription | 200 unchanged |

### Due list, billing day and billed periods (invoice-service contract)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| BIL-DUE-01 | P1 | `asOf` = the `nextBillingAt` date | Subscription included |
| BIL-DUE-02 | P1 | `asOf` = the day before | Not included |
| BIL-DUE-03 | P2 | Non-ACTIVE subscriptions (SUSPENDED, CANCELLED…) | Never included |
| BIL-DUE-04 | P2 | Paging: `size` 1, page 0/1/2 | Stable order by ID, no duplicates or gaps |
| BIL-DUE-05 | P3 | `size` 0 or 5000 | Clamped to 1 and 1000 |
| BIL-DUE-06 | P2 | Missing / malformed `asOf` | 400 |
| BIL-DAY-01 | P1 | Set billing day 1 and 28 | 200; `billingDay` updated |
| BIL-DAY-02 | P2 | Billing day 0, 29, null | 400 |
| BIL-DAY-03 | P2 | On CANCELLED or REJECTED | 409 "A … subscription has no billing day" |
| BIL-PER-01 | P1 | Record period whose start equals the current next billing date | 200; `nextBillingAt` = periodEnd |
| BIL-PER-02 | P1 | Retry the same period | 200 no-op (idempotent) |
| BIL-PER-03 | P1 | Period start not matching the next billing date (stale) | 409 "Billing period … does not match the next billing date …" |
| BIL-PER-04 | P2 | periodEnd ≤ periodStart | 400 |
| BIL-PER-05 | P2 | Subscription not ACTIVE | 409 "Only ACTIVE subscriptions are billed…" |

### Suspend and cancel

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| BIL-SUS-01 | P1 | Suspend ACTIVE | 200; `SUSPENDED`; no longer in the due list |
| BIL-SUS-02 | P2 | Suspend twice | 200 unchanged |
| BIL-SUS-03 | P1 | Suspend CANCELLED | 409 |
| BIL-SUS-04 | P3 | Suspend a WAITING_* subscription | Allowed today; confirm intended behaviour, and note there is no resume endpoint |
| BIL-CAN-01 | P1 | Cancel from any status | 200; `CANCELLED`; `nextBillingAt` null |
| BIL-CAN-02 | P1 | Cancel twice | 200 unchanged |
| BIL-CAN-03 | P2 | Unknown orderId on any action | 404 |

## Security note

Direct calls are unauthenticated (BIL-SEC-01, P1): verify the port is private and the admin proxy requires an agent token.
