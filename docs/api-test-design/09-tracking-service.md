# 09 — Tracking Service

**Port** 8088 · **Store** MongoDB `tracking_db.lifecycle_events` · **Consumes** Kafka `order-lifecycle-events`
(group `tracking-service`) · **Dead letters** `order-lifecycle-events.DLT`.

A read model of order lifecycle events. It stores each valid event once (by `eventId`) and serves an
order's history and a summary per order. Read-only API.

## Endpoints

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| GET | `/api/tracking/orders` | 200 | One summary per tracked order, most recently updated first |
| GET | `/api/tracking/orders/{orderId}/events` | 200 | The order's events, oldest first (empty list if none) |

CORS allows GET only, from the admin console origins (5174). No service-level authentication.

## Event contract (consumer input)

| Field | Rule |
| --- | --- |
| schemaVersion | Must be `1` |
| eventId | Required; primary key (repeat = overwrite, no duplicate) |
| orderId | Required; must equal the Kafka record key |
| customerId, eventType, status, occurredAt | Required |
| detail | Optional |

An invalid record is retried twice, 1 s apart, then published to `order-lifecycle-events.DLT` on the same partition.

## Summary model

`orderId`, `customerId`, `workflowStatus`, `paymentStatus`, `activationStatus`, `fulfillmentStatus`,
`billingStatus`, `updatedAt`. Each status is the **latest** event's status for that type
(`ORDER_WORKFLOW`, `PAYMENT`, `ESIM_ACTIVATION`, `FULFILLMENT`, `BILLING`), or `NOT_STARTED` if none.

## Functional scenarios

### API

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| TRK-API-01 | P1 | Events for an order that has run through checkout | 200; sorted by `occurredAt` ascending |
| TRK-API-02 | P1 | Events for an unknown order | 200 empty list (not 404) |
| TRK-API-03 | P1 | Summary after payment approved | `workflowStatus` `AWAITING_DELIVERY`, `paymentStatus` `COMPLETED`, others `NOT_STARTED` or `REQUESTED` |
| TRK-API-04 | P1 | Summary of a completed order | workflow `COMPLETED`, payment `COMPLETED`, fulfilment `DELIVERED`, activation `ACTIVE`, billing `ACTIVE` |
| TRK-API-05 | P2 | Summary ordering | Most recent `updatedAt` first |
| TRK-API-06 | P2 | Summary of a cancelled / failed order | workflow `CANCELLED` / `FAILED`, step statuses as at cancellation |

### Consumer (publish test records directly to Kafka)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| TRK-CON-01 | P1 | Valid event | Appears in the events API within a few seconds |
| TRK-CON-02 | P1 | Same `eventId` published twice | Stored once |
| TRK-CON-03 | P1 | Key ≠ orderId | Not stored; record lands on the DLT |
| TRK-CON-04 | P1 | `schemaVersion` = 2 | Not stored; DLT |
| TRK-CON-05 | P2 | Each required field missing in turn | Not stored; DLT |
| TRK-CON-06 | P2 | Non-JSON / undeserialisable payload | DLT; consumer keeps processing later records |
| TRK-CON-07 | P2 | Poison record followed by a valid one on the same partition | Valid record still consumed (no stuck partition) |
| TRK-CON-08 | P3 | Events published out of time order | Events API still returns them by `occurredAt` |
| TRK-CON-09 | P3 | Service restarted with backlog | Catches up (offsets committed per record; `auto-offset-reset` earliest) |

### Security

| ID | P | Scenario | Expected / note |
| --- | --- | --- | --- |
| TRK-SEC-01 | P1 | Direct call without a token | Succeeds; returns every customer's events. Port must stay private; admin proxy requires an agent token |
| TRK-SEC-02 | P2 | POST/PUT/DELETE on the API | 405 |

## Known gaps

- The summary endpoint loads every event into memory; consider a performance test with a large event volume.
- No pagination or filtering by customer.
