# 02 — Product Service

**Port** 8081 · **Store** MongoDB `product_db` (`products`, `esim_plans`) · **Role** catalogue of phones and eSIM plans.

Public read of active items; JWT-protected administration (scope `catalog:write`). Seeds two phones
and two plans when the collections are empty.

## Endpoints

| Method | Path | Auth | Success | Purpose |
| --- | --- | --- | --- | --- |
| GET | `/api/products` | Public | 200 | Active products only |
| GET | `/api/products/{id}` | Public | 200 / 404 | One **active** product |
| GET | `/api/esim-plans` | Public | 200 | Active plans only |
| GET | `/api/admin/products` | `catalog:write` | 200 | All products, including inactive |
| POST | `/api/admin/products` | `catalog:write` | 201 | Create product |
| PUT | `/api/admin/products/{id}` | `catalog:write` | 200 | Full update |
| DELETE | `/api/admin/products/{id}` | `catalog:write` | 204 | Hard delete |
| POST | `/api/admin/esim-plans` | `catalog:write` | 201 | Create plan |
| PUT | `/api/admin/esim-plans/{id}` | `catalog:write` | 200 | Full update |
| DELETE | `/api/admin/esim-plans/{id}` | `catalog:write` | 204 | Hard delete |
| any | anything else | — | — | Denied |

## Data rules

**Product**

| Field | Rule | Normalisation |
| --- | --- | --- |
| sku | Required, ≤ 64, unique | Trimmed, upper-cased (uniqueness is case-insensitive) |
| name | Required, ≤ 120 | Trimmed |
| description | Required, ≤ 1000 | Trimmed |
| storage, finish | Required | Trimmed |
| price | Required, > 0 | — |
| features | 1–20 items, each not blank, ≤ 200 | Each trimmed |
| active | boolean (defaults to false if omitted) | — |

**eSIM plan**: `code` (required, ≤ 64, unique, trimmed and upper-cased), `name` (≤ 120), `description` (≤ 1000),
`monthlyPrice` (> 0), `active`.

**Seed data** (only on empty collections): `IPHONE-18-PRO-512` 899.00, `IPHONE-18-PRO-MAX-1TB` 1299.00,
`LIMITED-2GB-DAY` 14.99, `UNLIMITED` 29.99, all active.

## Functional scenarios

### Public catalogue

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PRD-PUB-01 | P1 | List products without a token | 200; every item has `active` = true |
| PRD-PUB-02 | P1 | Get an active product by ID | 200; fields match the admin view |
| PRD-PUB-03 | P1 | Get an inactive product by ID | 404 |
| PRD-PUB-04 | P2 | Get an unknown product ID | 404 with the service's error body |
| PRD-PUB-05 | P1 | List plans | 200; only active plans |
| PRD-PUB-06 | P2 | Fresh environment | Seeded phones and plans present |
| PRD-PUB-07 | P2 | Price precision | Decimal prices returned exactly (for example 899.00, 14.99) |

### Product administration

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PRD-ADM-01 | P1 | Create a valid product | 201; server-generated `id`; SKU upper-cased |
| PRD-ADM-02 | P1 | Create an active product, then GET it publicly | Visible in `/api/products` and by ID |
| PRD-ADM-03 | P1 | Create with `active` false | Absent from the public list; present in the admin list |
| PRD-ADM-04 | P1 | Duplicate SKU (any case or spacing) | 409 "Product with this SKU already exists" |
| PRD-ADM-05 | P2 | Client-supplied `id` on create | Ignored; new ID generated |
| PRD-ADM-06 | P1 | Update all fields | 200; changes persisted |
| PRD-ADM-07 | P2 | Update SKU to another product's SKU | 409 |
| PRD-ADM-08 | P2 | Update keeping own SKU | 200 |
| PRD-ADM-09 | P1 | Deactivate through PUT | Public GET by ID now 404 |
| PRD-ADM-10 | P2 | Update or delete an unknown ID | 404 |
| PRD-ADM-11 | P1 | Delete | 204; admin list no longer contains it |
| PRD-ADM-12 | P2 | Validation: price 0 or negative, blank name, 0 features, 21 features, a blank feature, a feature of 201 chars, SKU of 65 chars | 400 with `field: message` |
| PRD-ADM-13 | P3 | Field boundaries: name 120, description 1000, 20 features | 201 |

### eSIM plan administration

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PRD-PLN-01 | P1 | Create a valid plan | 201; code upper-cased |
| PRD-PLN-02 | P1 | Duplicate code | 409 "eSIM plan with this code already exists" |
| PRD-PLN-03 | P1 | Update and deactivate | 200; inactive plan disappears from the public list |
| PRD-PLN-04 | P2 | Update code to another plan's code | 409 |
| PRD-PLN-05 | P2 | Update or delete an unknown ID | 404 "eSIM plan not found with ID: …" |
| PRD-PLN-06 | P2 | `monthlyPrice` 0 / missing | 400 |
| PRD-PLN-07 | P3 | `GET /api/admin/esim-plans` | Denied: there is no admin plan list, so inactive plans cannot be listed (known gap) |

## Security scenarios

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| PRD-SEC-01 | P1 | Admin endpoint with no token | 401 |
| PRD-SEC-02 | P1 | Admin endpoint with an ordinary customer token | 403 |
| PRD-SEC-03 | P1 | Admin endpoint with an `orders:operate`-only token | 403 |
| PRD-SEC-04 | P1 | Admin endpoint with a `catalog:write` token | Allowed |
| PRD-SEC-05 | P2 | Expired, wrong-secret or wrong-issuer token | 401 |
| PRD-SEC-06 | P2 | Write to public paths (`POST /api/products`, `DELETE /api/products/{id}`) | Denied (401/403) |
| PRD-SEC-07 | P3 | Any unmapped path | Denied, never 200 |

## Integration notes

- orchestration-service reads `/api/products/{id}` and `/api/esim-plans` to price storefront orders,
  so deactivating an item must make new orders for it fail (see ORC-STO-04/05).
- Deleting or repricing a product does not change existing orders (they store a price snapshot).

## Known gaps

- Hard delete only; no soft delete or audit trail.
- No pagination or filtering on lists.
