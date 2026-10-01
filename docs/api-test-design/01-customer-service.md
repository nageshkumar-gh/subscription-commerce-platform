# 01 — Customer Service

**Port** 8080 · **Store** MongoDB `customer_db.customers` · **Role** identity provider for the platform.

It registers customers, authenticates them with BCrypt passwords, issues HS256 JWTs, manages the
signed-in customer's own profile, and verifies tokens and scopes for the admin console's nginx edge.

## Endpoints

| Method | Path | Auth | Success | Purpose |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | 201 | Create a customer and return a token |
| POST | `/api/auth/login` | Public | 200 | Return a token (with configured scopes) |
| GET | `/api/auth/verify?scope=` | Bearer | 204 + header `X-Customer-Id` | Edge token check; 403 if scope missing |
| GET | `/api/customers/me` | Bearer | 200 | Own profile |
| PUT | `/api/customers/me` | Bearer | 200 | Replace own name, email, phone |
| DELETE | `/api/customers/me` | Bearer | 204 | Delete own profile |

## Data rules

| Field | Rule | Normalisation |
| --- | --- | --- |
| name | Required, not blank | Trimmed |
| email | Required, valid email, unique | Trimmed and lower-cased (uniqueness is case-insensitive) |
| phone | Required, 7–15 digits only | Trimmed |
| password | Required, 8–72 characters (register only) | Stored as BCrypt hash, never returned |

**Response `AuthResponse`**: `accessToken`, `tokenType` = `Bearer`, `expiresIn` = 3600, `customer {id, name, email, phone, active}`.

**Token claims**: `iss` = `customer-service`, `sub` = customer ID, `email`, `iat`, `exp` (iat + 3600 s), and
`scope` only when configured: `catalog:write` (catalogue admin) and/or `orders:operate` (operations agent),
space-separated.

## Functional scenarios

### Registration

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| CUS-REG-01 | P1 | Register with valid name, email, phone, password | 201; body has token, `tokenType` Bearer, `expiresIn` 3600, customer with `id` and `active` = true |
| CUS-REG-02 | P1 | Response never exposes password or hash | No `password` / `passwordHash` field in the body |
| CUS-REG-03 | P1 | Register the same email twice | Second call 409, message "Customer with this email already exists" |
| CUS-REG-04 | P2 | Register `Qa@Example.TEST` after `qa@example.test` | 409 (case-insensitive) |
| CUS-REG-05 | P2 | Email with surrounding spaces and upper case | 201; stored email is trimmed and lower-cased |
| CUS-REG-06 | P1 | Registration token carries no `scope` claim even if the ID is later configured as admin/agent | Decoded token has no `scope` |
| CUS-REG-07 | P2 | Missing / blank name | 400 "Customer name is required" |
| CUS-REG-08 | P2 | Invalid email format | 400 "Email format is invalid" |
| CUS-REG-09 | P2 | Phone with letters, 6 digits, 16 digits, `+353…` | 400 "Phone number must contain 7 to 15 digits" |
| CUS-REG-10 | P2 | Phone boundaries 7 and 15 digits | 201 |
| CUS-REG-11 | P2 | Password 7 chars / 73 chars | 400 "Password must contain 8 to 72 characters" |
| CUS-REG-12 | P3 | Password exactly 8 and 72 chars | 201 |
| CUS-REG-13 | P3 | Extra unknown fields (for example `"scope":"catalog:write"`, `"active":false`, `"id":"x"`) | Ignored; no privilege, server-generated ID, `active` true |
| CUS-REG-14 | P3 | Concurrent registrations with the same email | Exactly one 201, the rest 409 (unique index) |

### Login

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| CUS-LOG-01 | P1 | Valid credentials | 200 with a new token for the same customer ID |
| CUS-LOG-02 | P1 | Wrong password | 401 "Email or password is incorrect" |
| CUS-LOG-03 | P1 | Unknown email | 401 with the **same** message as a wrong password (no account enumeration) |
| CUS-LOG-04 | P2 | Email in different case or with spaces | 200 (normalised) |
| CUS-LOG-05 | P1 | Customer ID listed in `CATALOG_ADMIN_CUSTOMER_IDS` | Token `scope` contains `catalog:write` |
| CUS-LOG-06 | P1 | Customer ID listed in `AGENT_CUSTOMER_IDS` | Token `scope` contains `orders:operate` |
| CUS-LOG-07 | P2 | ID listed in both | `scope` = `catalog:write orders:operate` |
| CUS-LOG-08 | P2 | Ordinary customer | No `scope` claim |
| CUS-LOG-09 | P2 | Login after the customer deleted their profile | 401 |
| CUS-LOG-10 | P3 | Blank password / invalid email format | 400 |
| CUS-LOG-11 | P3 | Token timing: `exp - iat` | 3600 s (or the configured TTL) |

### Profile (`/me`)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| CUS-ME-01 | P1 | GET with a valid token | 200; profile matches the token subject |
| CUS-ME-02 | P1 | PUT valid name, email, phone | 200; changes persisted (confirm with GET) |
| CUS-ME-03 | P1 | PUT an email owned by another customer | 409 |
| CUS-ME-04 | P2 | PUT keeping own email (any case) | 200 (no self-conflict) |
| CUS-ME-05 | P2 | PUT with invalid fields | 400 with the same messages as registration |
| CUS-ME-06 | P2 | After changing email, login with the new email | 200; old email gives 401 |
| CUS-ME-07 | P2 | Password is unchanged by PUT | Login with old password still works |
| CUS-ME-08 | P1 | DELETE | 204; then GET with the same token gives 404 "Customer not found…" |
| CUS-ME-09 | P2 | DELETE twice with the same token | Second call 404 |

### Token verification (edge)

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| CUS-VER-01 | P1 | Valid token, no `scope` parameter | 204, header `X-Customer-Id` = subject |
| CUS-VER-02 | P1 | Agent token, `scope=orders:operate` | 204 |
| CUS-VER-03 | P1 | Customer token, `scope=orders:operate` | 403 |
| CUS-VER-04 | P1 | No token / malformed token | 401 |
| CUS-VER-05 | P2 | `scope=orders` (prefix of a granted scope) | 403 (exact match required) |
| CUS-VER-06 | P3 | `scope=` blank | 204 (treated as no scope) |

## Security scenarios

| ID | P | Scenario | Expected |
| --- | --- | --- | --- |
| CUS-SEC-01 | P1 | `/api/customers/me` without a token | 401 |
| CUS-SEC-02 | P1 | Expired token | 401 |
| CUS-SEC-03 | P1 | Token signed with a different secret | 401 |
| CUS-SEC-04 | P2 | Token with a different `iss` | 401 |
| CUS-SEC-05 | P2 | Token using `alg: none` | 401 |
| CUS-SEC-06 | P2 | Tampered payload (changed `sub`) | 401 |
| CUS-SEC-07 | P2 | No endpoint lists or fetches other customers by ID | Any `/api/customers` path other than `/me` gives 401 / 404 / 405, never data |
| CUS-SEC-08 | P3 | CORS: allowed origins only (5173 / 5174 by default); `*` is rejected at startup | Preflight from another origin gets no CORS headers |
| CUS-SEC-09 | P3 | Brute force: many wrong passwords | Note: there is **no lockout or rate limiting**; record as a known gap |

## Notes and known gaps

- No password change or reset endpoint; no admin-side customer listing.
- No rate limiting or lockout on login.
- Scopes are recomputed only at login: removing an ID from configuration does not revoke tokens already issued (they stay valid until `exp`).
