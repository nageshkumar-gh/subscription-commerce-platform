# 05 — Network Service (eSIM activation)

**Port** 8084 · **Store** MongoDB `network_db.activations` · **Role** eSIM provisioning, one activation per order.

## Endpoints

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| POST | `/api/activations` | 201 (also for an idempotent repeat) | Create or return the activation for an order |
| GET | `/api/activations` | 200 | All activations |
| GET | `/api/activations?orderId=` | 200 / 404 | The order's activation |
| POST | `/api/activations/{id}/approve` | 200 | Advance one step (optional reason) |
| POST | `/api/activations/{id}/reject` | 200 | Fail the activation (reason required) |

No service-level authentication. There is no GET-by-ID endpoint.

## Data rules

**Create**: `orderId`, `customerId`, `planId`, all required and not blank.

**Server fields**: `id`, `iccid` (starts with `8944`, 19 characters), `status` = `QUEUED`, `requestedAt`,
`readyAt` (= requestedAt + `network.activation-delay-seconds`, default 30 s), `activatedAt`,
`statusChangedAt`, `statusReason`.

## Status model

`QUEUED → ACTIVATING → ACTIVE` (each approve moves one step) · `QUEUED | ACTIVATING → FAILED` (reject).
ACTIVE and FAILED are terminal.

**Auto-advance mode** (`NETWORK_AUTO_ADVANCE=true`): every ~2 s QUEUED moves to ACTIVATING, and
ACTIVATING moves to ACTIVE once `readyAt` has passed.

## Functional scenarios

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| NET-CRT-01 | P1 | Create for a new order | 201; `QUEUED`; `iccid` starts with `8944` and is 19 characters; `readyAt` ≈ requestedAt + 30 s |
| NET-CRT-02 | P1 | Repeat with identical orderId, customerId, planId | 201; **same** `id` and `iccid` (idempotent) |
| NET-CRT-03 | P1 | Same orderId, different customerId or planId | 409 "An activation already exists for this order with different details" |
| NET-CRT-04 | P2 | Missing / blank field | 400 `field: message` |
| NET-CRT-05 | P3 | Parallel creates for one order | One record; all responses carry the same ID |
| NET-CRT-06 | P3 | ICCID uniqueness across many activations | No duplicates |
| NET-RD-01 | P1 | Get by orderId | 200; matches create |
| NET-RD-02 | P1 | Unknown orderId | 404 "Activation not found for order …" |
| NET-RD-03 | P3 | List all | Contains created records |
| NET-APR-01 | P1 | Approve QUEUED | 200; `ACTIVATING`; `statusChangedAt` set |
| NET-APR-02 | P1 | Approve ACTIVATING | 200; `ACTIVE`; `activatedAt` set |
| NET-APR-03 | P1 | Approve ACTIVE or FAILED | 409 "Activation is already … and cannot be advanced" |
| NET-APR-04 | P2 | Approve with reason / without body | Reason saved trimmed / `statusReason` null |
| NET-APR-05 | P2 | Approve unknown ID | 404 |
| NET-REJ-01 | P1 | Reject QUEUED or ACTIVATING with a reason | 200; `FAILED`; `statusReason` = reason |
| NET-REJ-02 | P1 | Reject ACTIVE or FAILED | 409 "… cannot be rejected" |
| NET-REJ-03 | P1 | Reject with blank reason / no body | 400 |
| NET-REJ-04 | P3 | Reason over 500 chars | 400 |

### Auto-advance suite (run with `NETWORK_AUTO_ADVANCE=true`)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| NET-AUTO-01 | P2 | Create and poll | Moves to ACTIVATING within a few seconds, then ACTIVE no earlier than `readyAt` |
| NET-AUTO-02 | P2 | Reject while ACTIVATING | Stays FAILED; the processor does not revive it |
| NET-AUTO-03 | P2 | Default mode (`false`) | Activation stays QUEUED indefinitely without approval |

## Security note

| ID | P | Scenario | Expected / note |
| --- | --- | --- | --- |
| NET-SEC-01 | P1 | Direct unauthenticated call | Succeeds; port must stay private. Through admin proxy, an agent token is needed |

## Integration notes

- The workflow requests activation only **after** delivery is DELIVERED.
- billing-service checks that the activation ID matches and is ACTIVE before billing can start.
- Cancellation rejects a non-terminal activation ("eSIM activation stopped"); an ACTIVE one is reported
  as "eSIM is active and must be deactivated manually" (there is no deactivate API).
