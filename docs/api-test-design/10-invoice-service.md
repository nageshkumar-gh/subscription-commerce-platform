# 10 — Invoice Service

**Port** 8089 · **Store** PostgreSQL `invoice` (Flyway; Spring Batch metadata in the same DB) ·
**Calls** billing (8086), payment (8083) · **Zone** `Europe/Dublin` · **VAT** 23 % (prices are VAT-inclusive) · **Currency** EUR.

A daily Spring Batch job (`monthlyInvoicing`) that invoices due subscriptions:
**snapshot** due subscriptions into run items → for each item **issue** an invoice → **charge** it →
**advance** the subscription's billing period. Every step is idempotent, so a run can be repeated safely.

## Endpoints

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| GET | `/api/billing-runs?limit=` | 200 | Recent runs with counts (limit 1–200, default 30) |
| POST | `/api/billing-runs` | 202 | Start a manual run; optional body `{runDate}` (default today) |
| GET | `/api/billing-runs/preview?runDate=` | 200 | Dry run: what would be invoiced, nothing changed |
| GET | `/api/billing-runs/{runId}/items` | 200 | Every subscription the run picked up and its outcome |
| GET | `/api/billing-schedule` | 200 | `{enabled, runTime, zone, nextRunAt, updatedAt}` |
| PUT | `/api/billing-schedule` | 200 | `{enabled, runTime "HH:mm"}` |
| GET | `/api/invoices?customerId=&orderId=&runId=&status=&limit=` | 200 | Search, newest first (limit 1–500, default 100) |
| GET | `/api/invoices/{invoiceNumber}` | 200 / 404 | One invoice |

Error body: `{status, message}`. No service-level authentication.

## Business rules

**Period**: an invoice issued on `periodStart` covers [periodStart, periodEnd), where periodEnd is the next
occurrence of the billing day (1–28) after periodStart.

**Amount**: a full calendar month = plan price. A shorter period (after a billing-day change) =
price × days ÷ days-in-start-month, rounded half-up to 2 dp. `net` = total ÷ 1.23 (2 dp), `vat` = total − net,
`prorated` = true when not a full month.

**Invoice number**: `INV-<year>-<6-digit sequence>`; unique per (subscription, periodStart).

**Charge**: `POST /api/payments/charges` with key `invoice-<invoiceNumber>`. COMPLETED → invoice `PAID`;
anything else → `PAYMENT_FAILED` with `failureReason`. **The billing period advances either way.**

**Run item states**: `PENDING → INVOICED | FAILED`; message such as `PAID EUR 29.99`.

**Runs**: a scheduled run happens at most once a day (identified by date). Manual runs can be repeated the
same day (already-invoiced subscriptions are no longer due). Only one run at a time. Future dates are refused.

## Functional scenarios

### Preview

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| INV-PRE-01 | P1 | Preview on a subscription's due date | Line for it: billingDay, periodStart/End, net, VAT, total, `existingInvoice` null |
| INV-PRE-02 | P1 | Preview changes nothing | No invoice, charge or run created; `nextBillingAt` unchanged |
| INV-PRE-03 | P1 | Preview after the period is already invoiced | Subscription no longer due (absent), or if present `existingInvoice` set and excluded from `total` |
| INV-PRE-04 | P2 | Preview a future date | Allowed (planning) |
| INV-PRE-05 | P2 | Nothing due | `subscriptions` 0, `total` 0, empty lines |

### Run execution

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| INV-RUN-01 | P1 | Start a manual run with no body | 202; `runId`, `trigger` MANUAL, `runDate` today (Dublin) |
| INV-RUN-02 | P1 | Poll the run list until `status` COMPLETED | `counts.total` = due subscriptions; `invoiced` = total; `pending` 0 |
| INV-RUN-03 | P1 | Run items | One per due subscription; state INVOICED; `invoiceNumber` set |
| INV-RUN-04 | P1 | Effect on billing | Each subscription's `nextBillingAt` = periodEnd |
| INV-RUN-05 | P1 | Effect on payments | One COMPLETED charge per invoice, `invoiceId` = invoice number |
| INV-RUN-06 | P1 | Run again the same day | New run with 0 items; no new invoices or charges |
| INV-RUN-07 | P1 | `runDate` in the future | 400 "Runs cannot be dated in the future; use preview to plan ahead" |
| INV-RUN-08 | P2 | Start while another run is in progress | 409 "An invoicing run is already in progress" |
| INV-RUN-09 | P2 | Back-dated run (yesterday) | Allowed; invoices subscriptions due on or before that date |
| INV-RUN-10 | P2 | Subscription several months overdue | One invoice per run (one period); later runs pick up the next period |
| INV-RUN-11 | P1 | Customer configured to be declined | Invoice `PAYMENT_FAILED` with "Card declined (simulated)"; run item INVOICED; billing period still advances |
| INV-RUN-12 | P2 | billing-service rejects the period advance, or payment is down | Run item FAILED with message; other items in the run still processed |
| INV-RUN-13 | P2 | SUSPENDED / CANCELLED subscriptions | Never invoiced |
| INV-RUN-14 | P3 | More than 200 due subscriptions | All paged in from billing and all invoiced (chunk size 50) |
| INV-RUN-15 | P3 | Kill the service mid-run, restart, re-run | No duplicate invoices or charges (unique period, idempotent charge key, guarded advance) |
| INV-RUN-16 | P3 | Run items for an unknown run ID | 200 empty list |

### Calculation (data-driven)

| ID | P | Case | Expected |
| --- | --- | --- | --- |
| INV-CAL-01 | P1 | 29.99, full month | total 29.99, net 24.38, VAT 5.61, `prorated` false |
| INV-CAL-02 | P1 | 14.99, full month | total 14.99, net 12.19, VAT 2.80 |
| INV-CAL-03 | P1 | Billing day changed from 15 to 25; next start the 15th of a 30-day month | 10-day period; total = price × 10 ÷ 30; `prorated` true |
| INV-CAL-04 | P2 | Billing day earlier than period start day (for example start 20th, day 5) | periodEnd = 5th of next month |
| INV-CAL-05 | P2 | February and 31-day start months | Proration denominator = days in the **start** month |
| INV-CAL-06 | P3 | Rounding half-up cases | Match the half-up rule exactly |
| INV-CAL-07 | P2 | Invoice number format and sequence | Matches `INV-YYYY-NNNNNN`; increases |

### Schedule

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| INV-SCH-01 | P1 | Get schedule | `zone` Europe/Dublin; `nextRunAt` null when disabled |
| INV-SCH-02 | P1 | Update enabled + runTime | 200; values persisted; `updatedAt` changes; seconds truncated |
| INV-SCH-03 | P2 | runTime later today, no run yet | `nextRunAt` = today at runTime |
| INV-SCH-04 | P2 | runTime already passed, no run yet | `nextRunAt` ≈ now; a SCHEDULED run starts within ~60 s |
| INV-SCH-05 | P2 | Scheduled run already happened today | `nextRunAt` = tomorrow at runTime; no second scheduled run |
| INV-SCH-06 | P2 | Disabled schedule | No scheduled run starts |
| INV-SCH-07 | P2 | Missing `enabled`, missing `runTime`, `runTime` "25:00" or "9am" | 400 |

### Invoice search

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| INV-SRC-01 | P1 | By customerId / orderId / runId | Only matching invoices, newest first |
| INV-SRC-02 | P2 | `status=paid` (lower case) | Matches `PAID` |
| INV-SRC-03 | P2 | Combined filters | AND semantics |
| INV-SRC-04 | P3 | `limit` 0 / 1000 | Clamped to 1 / 500 |
| INV-SRC-05 | P1 | Get by number / unknown number | 200 / 404 "Invoice not found" |

### Security

INV-SEC-01 (P1): direct calls are unauthenticated, including starting runs and changing the schedule.
Confirm the port is private and the admin proxy requires an agent token.
