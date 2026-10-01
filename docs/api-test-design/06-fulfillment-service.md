# 06 — Fulfillment Service (device delivery)

**Port** 8085 · **Store** MongoDB `fulfillment_db` · **Role** phone delivery lifecycle, one fulfilment per order.

## Endpoints

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| POST | `/api/fulfillments` | 201 (also for an idempotent repeat) | Create or return the fulfilment for an order |
| GET | `/api/fulfillments` | 200 | All fulfilments |
| GET | `/api/fulfillments?orderId=` | 200 / 404 | The order's fulfilment |
| POST | `/api/fulfillments/{id}/approve` | 200 | Advance one step (optional reason) |
| POST | `/api/fulfillments/{id}/reject` | 200 | Fail the fulfilment (reason required) |

No service-level authentication. There is no GET-by-ID endpoint.

## Data rules

**Create**: `orderId`, `customerId`, `productId`, all required and not blank.

**Server fields**: `id`, `trackingNumber` (`TRACK-` + 8 upper-case hex), `status` = `RECEIVED`, `createdAt`,
`nextTransitionAt` (createdAt + 15 s by default; cleared on approve/reject), `deliveredAt`,
`statusChangedAt`, `statusReason`.

## Status model

`RECEIVED → PREPARING → DISPATCHED → DELIVERED` (each approve moves one step) ·
any undelivered status `→ FAILED` (reject). DELIVERED and FAILED are terminal.

**Auto-advance mode** (`FULFILLMENT_AUTO_ADVANCE=true`): about every 2 s, a record whose `nextTransitionAt`
has passed moves one step and gets a new `nextTransitionAt` (+15 s) until DELIVERED.

## Functional scenarios

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| FUL-CRT-01 | P1 | Create for a new order | 201; `RECEIVED`; `trackingNumber` matches `TRACK-XXXXXXXX` |
| FUL-CRT-02 | P1 | Repeat with identical data | 201; same `id` and tracking number |
| FUL-CRT-03 | P1 | Same orderId, different customerId or productId | 409 "A fulfillment already exists for this order with different details" |
| FUL-CRT-04 | P2 | Missing / blank fields | 400 |
| FUL-CRT-05 | P3 | Parallel creates for one order | A single record |
| FUL-RD-01 | P1 | Get by orderId / unknown orderId | 200 / 404 "Fulfillment not found for order …" |
| FUL-APR-01 | P1 | Approve RECEIVED → PREPARING → DISPATCHED → DELIVERED | Each 200; `deliveredAt` set only at DELIVERED; `nextTransitionAt` null after each manual approve |
| FUL-APR-02 | P1 | Approve DELIVERED or FAILED | 409 "Fulfillment is already … and cannot be advanced" |
| FUL-APR-03 | P2 | Approve with / without a reason | Reason stored trimmed / null |
| FUL-APR-04 | P2 | Approve unknown ID | 404 |
| FUL-REJ-01 | P1 | Reject RECEIVED, PREPARING or DISPATCHED with a reason | 200; `FAILED`; reason stored |
| FUL-REJ-02 | P1 | Reject DELIVERED or FAILED | 409 |
| FUL-REJ-03 | P1 | Reject with blank reason / no body | 400 |

### Auto-advance suite (run with `FULFILLMENT_AUTO_ADVANCE=true`)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| FUL-AUTO-01 | P2 | Create and poll | Each step about 15 s apart; DELIVERED within about 45–50 s |
| FUL-AUTO-02 | P2 | Manually approve during auto mode | `nextTransitionAt` cleared, so that record no longer auto-advances (verify and document) |
| FUL-AUTO-03 | P2 | Reject during auto mode | Remains FAILED |

## Security note

| ID | P | Scenario | Expected / note |
| --- | --- | --- | --- |
| FUL-SEC-01 | P1 | Direct unauthenticated call | Succeeds; port must stay private. Through admin proxy, an agent token is needed |

## Integration notes

- The workflow requests fulfilment after payment is COMPLETED, and syncs the order to DISPATCHED and DELIVERED.
- billing-service needs this record DELIVERED (and its ID to match) before billing can start.
- Cancellation rejects an undelivered fulfilment ("delivery stopped"); a DELIVERED one is reported as
  "device already delivered; arrange a return".
