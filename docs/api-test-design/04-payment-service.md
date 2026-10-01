# 04 — Payment Service

**Port** 8083 · **Store** MongoDB `payment_db.payments` · **Role** provider-neutral payment boundary.

It handles checkout payment intents (one per order), operator approval or rejection standing in for a
payment provider, refund requests, and simulated monthly recurring charges (one per invoice).

## Endpoints

| Method | Path | Header | Success | Purpose |
| --- | --- | --- | --- | --- |
| POST | `/api/payments` | `Idempotency-Key` (required) | 201 new / 200 replay | Create a checkout payment intent (`PENDING`) |
| GET | `/api/payments` | — | 200 | All payments, newest first |
| GET | `/api/payments/{id}` | — | 200 / 404 | One payment |
| GET | `/api/payments?orderId=` | — | 200 / 404 | The order's **checkout** payment (not invoice charges) |
| GET | `/api/payments?customerId=` | — | 200 | A customer's payments, newest first |
| GET | `/api/payments?invoiceId=` | — | 200 / 404 | The charge for an invoice |
| POST | `/api/payments/{id}/approve` | — | 200 | PENDING → COMPLETED (optional reason) |
| POST | `/api/payments/{id}/reject` | — | 200 | PENDING → FAILED (reason required) |
| POST | `/api/payments/{id}/refund` | — | 200 | COMPLETED → REFUND_PENDING (reason required) |
| POST | `/api/payments/charges` | `Idempotency-Key` (required) | 201 new / 200 replay | Simulated monthly charge, completes or fails immediately |

No service-level authentication; reached by operators through the admin proxy (`orders:operate`).

## Data rules

**Create intent**: `orderId` (≤ 100), `customerId` (≤ 100), `amount` > 0, `currency` three letters
(stored upper-case), `paymentMethod` (≤ 50), `paymentToken` (≤ 512, **never stored or returned**).

**Recurring charge**: `invoiceId`, `orderId`, `customerId` (each ≤ 100), `amount` > 0, `currency` three letters.
Stored with `paymentMethod` = `SIMULATED_RECURRING`.

**Idempotency-Key**: required, not blank, at most 128 characters after trimming.

**Server fields**: `id`, `status`, `transactionReference` (`PAY-` + 8 upper-case hex chars), `createdAt`,
`updatedAt`, `statusReason`, `refundReason`.

## Status model

`PENDING → COMPLETED` (approve) · `PENDING → FAILED` (reject) · `COMPLETED → REFUND_PENDING` (refund).
`REFUNDED` exists but no API sets it (a future provider integration would).

## Functional scenarios

### Create checkout payment intent

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PAY-CRT-01 | P1 | Valid request with a new key | 201; status `PENDING`; `transactionReference` matches `PAY-XXXXXXXX`; currency upper-case |
| PAY-CRT-02 | P1 | `paymentToken` is not echoed or stored | No token field in response or in GET |
| PAY-CRT-03 | P1 | Replay: same key, same body | 200; same payment `id` |
| PAY-CRT-04 | P1 | Same key, different amount / order / customer / currency / method | 409 `PAYMENT_CONFLICT` "Idempotency key was already used for a different payment request" |
| PAY-CRT-05 | P2 | Replay where only the currency case differs (`eur` vs `EUR`) | 200 (case-insensitive match) |
| PAY-CRT-06 | P1 | New key, but the order already has a checkout payment | 409 "A payment already exists for this order" |
| PAY-CRT-07 | P1 | Missing `Idempotency-Key` header | 400 |
| PAY-CRT-08 | P2 | Blank key / 129-char key | 400 `INVALID_REQUEST` |
| PAY-CRT-09 | P2 | Key with surrounding spaces then the trimmed key | Treated as the same key (replay) |
| PAY-CRT-10 | P2 | Validation: amount 0, currency `EU` / `EURO` / `E1R`, blank method, token > 512 | 400 `VALIDATION_FAILED` with `fieldErrors` |
| PAY-CRT-11 | P3 | Parallel requests with the same key | One 201, the others 200 with the same ID |

### Read

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PAY-RD-01 | P1 | Get by ID / unknown ID | 200 / 404 `PAYMENT_NOT_FOUND` |
| PAY-RD-02 | P1 | Get by orderId | Returns the checkout payment, never an invoice charge for the same order |
| PAY-RD-03 | P2 | Get by orderId with no checkout payment | 404 "Payment not found for order: …" |
| PAY-RD-04 | P2 | List by customerId | Only that customer's, newest first; includes recurring charges |
| PAY-RD-05 | P2 | Get by invoiceId / unknown invoice | 200 / 404 |
| PAY-RD-06 | P3 | List all | Sorted by `createdAt` descending |

### Approve and reject

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PAY-DEC-01 | P1 | Approve PENDING (no body) | 200; `COMPLETED`; `statusReason` null |
| PAY-DEC-02 | P2 | Approve PENDING with a reason | Reason saved, trimmed |
| PAY-DEC-03 | P1 | Approve an already COMPLETED payment | 200 unchanged (idempotent) |
| PAY-DEC-04 | P1 | Approve FAILED / REFUND_PENDING | 409 "Only pending payments can be approved or rejected; this payment is …" |
| PAY-DEC-05 | P1 | Reject PENDING with a reason | 200; `FAILED`; `statusReason` = reason |
| PAY-DEC-06 | P1 | Reject with blank reason / with no body | 400 |
| PAY-DEC-07 | P2 | Reject an already FAILED payment | 200 unchanged |
| PAY-DEC-08 | P2 | Reject COMPLETED | 409 |
| PAY-DEC-09 | P3 | Reason of 501 chars | 400 |
| PAY-DEC-10 | P2 | Unknown ID | 404 |

### Refund

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PAY-REF-01 | P1 | Refund COMPLETED with a reason | 200; `REFUND_PENDING`; `refundReason` saved |
| PAY-REF-02 | P1 | Refund PENDING / FAILED / REFUND_PENDING | 409 "Only completed payments can be refunded" |
| PAY-REF-03 | P2 | Missing / blank reason, reason > 500 | 400 |

### Recurring charges

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PAY-CHG-01 | P1 | Charge for a normal customer | 201; `COMPLETED`; method `SIMULATED_RECURRING`; `statusReason` "Monthly charge for invoice …" |
| PAY-CHG-02 | P1 | Charge for a customer in `payment.simulated-decline-customers` | 201; `FAILED`; `statusReason` "Card declined (simulated)" |
| PAY-CHG-03 | P1 | Same key, same invoice | 200; same payment (no double charge) |
| PAY-CHG-04 | P1 | Different key, same invoice | 200; existing payment returned (no double charge) |
| PAY-CHG-05 | P1 | Same key, different invoice | 409 |
| PAY-CHG-06 | P2 | Several invoice charges and one checkout payment on the same order | All coexist; `?orderId=` still returns only the checkout payment |
| PAY-CHG-07 | P2 | Missing header / validation errors | 400 |

## Security and robustness

| ID | P | Scenario | Expected / note |
| --- | --- | --- | --- |
| PAY-SEC-01 | P1 | Direct call without a token | Succeeds (no service auth); port must stay private |
| PAY-SEC-02 | P1 | Through admin proxy without an agent token | 401 / 403 |
| PAY-SEC-03 | P2 | Startup on a database that has the old unique `orderId` index | Index dropped and replaced by `orderId_lookup` (non-unique), so invoice charges succeed |

## Integration notes

- orchestration-service creates the checkout intent with key `order-<orderId>`, currency EUR, method `SIMULATED_CARD`.
- invoice-service charges with key `invoice-<invoiceNumber>`.
- billing-service reads `?orderId=` and requires `COMPLETED` before billing can be approved.
- Cancellation compensation rejects a PENDING payment ("payment voided") or refunds a COMPLETED one ("refund requested").
