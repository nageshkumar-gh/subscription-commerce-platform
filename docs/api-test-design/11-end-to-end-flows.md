# 11 — End-to-End Flows

Cross-service journeys run through the real edges: the **web-ui proxy** (customer) and the
**admin-ui proxy** (operations agent). Each flow lists its steps and what to check after each one,
in which services. Use polling with timeouts for every asynchronous check
(workflow ~10 s, billing reconciler ~5 s, Kafka → tracking a few seconds).

## Actors

| Actor | How obtained | Edge |
| --- | --- | --- |
| Customer A, Customer B | Register, then log in (web-ui `/api/auth/*`) | web-ui (`/api/customers`, `/api/products`, `/api/esim-plans`, `/api/me`) |
| Catalogue admin | Login of an ID in `CATALOG_ADMIN_CUSTOMER_IDS` | product-service `/api/admin/**` |
| Operations agent | Login of an ID in `AGENT_CUSTOMER_IDS` | admin-ui (all operations APIs) |

## E2E-01 Happy path: order to active subscription (P1)

| # | Actor / call | Verify |
| - | --- | --- |
| 1 | Customer registers and logs in | Token; `/api/customers/me` returns profile |
| 2 | Browse products and plans | Active items only |
| 3 | `POST /api/me/orders` | Order `PENDING_PAYMENT` with catalogue prices and total |
| 4 | `POST /api/me/orders/{id}/checkout` | 202 STARTED; workflow `WAITING_FOR_PAYMENT`; payment `PENDING`; tracking has STARTED, WAITING_FOR_PAYMENT, PAYMENT PENDING |
| 5 | Agent approves payment | Order `PAID`; workflow `AWAITING_DELIVERY`; fulfilment `RECEIVED` |
| 6 | Agent approves fulfilment three times | Order `DISPATCHED`, then `DELIVERED`; workflow `AWAITING_ACTIVATION`; activation `QUEUED` |
| 7 | Agent approves activation twice | Order `ACTIVATED`; workflow `AWAITING_BILLING_APPROVAL`; subscription created, reconciles to `READY_FOR_BILLING` |
| 8 | Agent approves billing | Subscription `ACTIVE` with `nextBillingAt` = +1 month; order `COMPLETED`; workflow `COMPLETED` |
| 9 | Customer views details and subscriptions | All five records present and final; events in order; subscription listed |
| 10 | Tracking summary | workflow COMPLETED, payment COMPLETED, fulfilment DELIVERED, activation ACTIVE, billing ACTIVE |

## E2E-02 Monthly invoicing (P1)

Precondition: E2E-01 completed. A newly approved subscription is first due one month later, and runs
cannot be dated in the future, so the framework needs a **test hook** to make it due now: either set the
subscription's `nextBillingAt` to today directly in `billing_db` (test environment only), or run the
environment with a shifted clock. Changing the billing day does **not** change `nextBillingAt`, so it
cannot make a subscription due. This is a testability gap worth raising with the developers.

| # | Call | Verify |
| - | --- | --- |
| 1 | Agent: preview for the due date | One line, full-month amount, VAT split |
| 2 | Agent: start run | 202; run COMPLETED; one item INVOICED |
| 3 | Invoice search by orderId | One invoice `PAID`, with payment ID and reference |
| 4 | Payment by invoiceId | `COMPLETED`, `SIMULATED_RECURRING` |
| 5 | Subscription | `nextBillingAt` moved to periodEnd |
| 6 | Run again | No new invoice or charge |

Variant **E2E-02b** (P1): repeat for the declined customer. Invoice `PAYMENT_FAILED`, payment `FAILED`,
period still advanced.
Variant **E2E-02c** (P2): change the billing day, then run. Prorated invoice for the shorter period.

## E2E-03 Rejections at each stage (P1)

For each stage, reject with a reason and check:

| Stage rejected | Workflow | Order | Event carrying the reason |
| --- | --- | --- | --- |
| Payment | FAILED | FAILED | PAYMENT FAILED |
| Fulfilment | FAILED | FAILED | FULFILLMENT FAILED |
| Activation | FAILED | FAILED | ESIM_ACTIVATION FAILED |
| Billing | FAILED | FAILED | BILLING REJECTED |

Then check that checkout again returns `FAILED` (no restart) and that the operator can still cancel (compensation runs).

## E2E-04 Cancellation at each stage (P1)

Run the compensation matrix in [08-orchestration-service.md](08-orchestration-service.md) (ORC-CMP-01 to 04) end to end,
once as the **customer** (`/api/me/orders/{id}/cancel`, reason prefixed `Customer: `) and once as the **agent**
(`/api/workflows/orders/{id}/cancel`). Also:

- Cancel before checkout: immediate `CANCELLED`; checkout afterwards gives 409.
- Customer tries to cancel after billing is ACTIVE: 409; agent cancel gives 409 "…cancel its subscription instead";
  agent then cancels via `/api/subscriptions/{orderId}/cancel`, which succeeds and removes it from the due list.

## E2E-05 Idempotency across the flow (P1)

| Repeat | Expected |
| --- | --- |
| Checkout three times | One workflow, one payment intent |
| Approve payment twice | Second call 200 unchanged; no duplicate events |
| Workflow-created payment, then agent tries to create another for the same order | 409 |
| Invoice run twice | One invoice and one charge per period |

## E2E-06 Access control at the edges (P1)

| Check | Expected |
| --- | --- |
| Customer token on any admin-ui operations path | 403 (from `/api/auth/verify?scope=orders:operate`) |
| No token on admin-ui operations path | 401 |
| Agent token on admin-ui operations path | Allowed |
| web-ui proxy: `/api/orders`, `/api/payments`, `/api/workflows` | Not routed (SPA fallback or 404), never reaches the service |
| Customer B on customer A's order via `/api/me/orders/{id}` | 404 |
| Ordinary customer token on product admin | 403 |
| **Deployment check**: internal service ports (8082–8089) not reachable from outside the host network | Connection refused / filtered |

## E2E-07 Resilience (P2–P3)

| Scenario | Expected |
| --- | --- |
| Stop payment-service while the workflow waits for payment; restart within 10 min | Saga resumes; not failed |
| Stop tracking-service during a flow; restart | Catches up from Kafka; history complete; storefront showed empty events meanwhile |
| Restart orchestration-service mid-saga | Temporal resumes the workflow at the same state |
| Stop network-service; billing reconciler | Subscription status unchanged; recovers |
| Stop billing during an invoice run | Affected items FAILED; re-run later invoices them without duplicates |
| Agent cancels while a dependency is down and the workflow never started | 503; retry later succeeds |

## Suggested execution order and tagging

1. Smoke (all services' health and api-docs).
2. Component suites per service (docs 01–10), runnable in parallel with unique data.
3. Security suites.
4. E2E-01, E2E-03, E2E-04, E2E-05, E2E-06 on every build.
5. E2E-02 and E2E-07 nightly (slower, environment-sensitive).

Tag scenarios with service, priority (P1/P2/P3), layer (smoke / component / security / e2e / resilience)
and `async` where polling is involved, so pipelines can select subsets.
