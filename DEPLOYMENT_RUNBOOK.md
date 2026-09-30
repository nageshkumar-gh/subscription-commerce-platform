# EC2 standalone Compose deployment runbook

This runbook deploys the platform from the repository root on one test EC2
host. It follows the project policy: **stop services, pull/change, validate,
build while stopped, then start and verify**. No local backup copy is created.

## Capacity and exposure

- `t3.medium` (4 GiB RAM, 30 GiB gp3) is suitable for a staged development
  slice such as customer + product + web UI + Datadog. Stop running services
  before builds and do not run the whole platform at once on this size.
- Use at least `t3.large` (8 GiB RAM, 40–50 GiB gp3) for the full single-host
  test stack including nine services, eight MongoDB containers, Kafka, Temporal,
  PostgreSQL, two UIs, and Datadog. Managed/multi-host infrastructure is the
  production direction; a single EC2 host remains a single point of failure.
- Public inbound ports: `22` from the operator IP and `80` for the customer UI.
  The admin UI is not safe for public exposure until authentication exists.
- Loopback-only backend host ports: customer `8080`, product `8081`, network
  `8084`, fulfilment `8085`, billing `8086`, orchestration `8087`, and tracking
  `8088`. MongoDB, Kafka, Temporal, and PostgreSQL have no public host ports.
- Order `8082` and payment `8083` currently have no host publication; access
  them through the UI proxy/shared Docker network or `docker exec` tests.

## One-time host preparation

From the repository root, create the network before any stack that declares it
as external:

```bash
docker network inspect subscription-platform >/dev/null 2>&1 || \
  docker network create subscription-platform
```

Create the uncommitted environment file:

```bash
cp .env.datadog.example .env.datadog
chmod 600 .env.datadog
vim .env.datadog
```

Set the Datadog key/site, a stable public UI origin, a strong shared JWT secret,
and any catalogue-admin customer IDs. Confirm `.env.datadog` does not appear in
`git status --short`.

## Critical Compose rule

Run every Compose file as a standalone command from the repository root:

```bash
docker compose --env-file .env.datadog -f product-service/compose.yaml config --quiet
```

Never merge files like this:

```text
docker compose -f customer-service/compose.yaml -f product-service/compose.yaml ...
```

Each file's `build: .` is resolved from its own service directory. Combining
files changes merge behavior and can select the wrong build context, service,
volume, or network definition.

## Staged deployment on t3.medium

Start with the customer/catalogue slice. If containers already exist, stop them
first; a first deployment can skip the stop command.

```bash
docker stop web-ui product-service product-mongodb customer-service customer-mongodb datadog-agent 2>/dev/null || true
git pull --ff-only origin main

docker compose --env-file .env.datadog -f customer-service/compose.yaml config --quiet
docker compose --env-file .env.datadog -f product-service/compose.yaml config --quiet
docker compose --env-file .env.datadog -f web-ui/compose.yaml config --quiet
docker compose --env-file .env.datadog -f datadog-compose.yaml config --quiet

docker compose --progress=plain --env-file .env.datadog -f customer-service/compose.yaml build customer-service
docker compose --progress=plain --env-file .env.datadog -f product-service/compose.yaml build product-service
docker compose --progress=plain --env-file .env.datadog -f web-ui/compose.yaml build web-ui

docker compose --env-file .env.datadog -f customer-service/compose.yaml up -d
docker compose --env-file .env.datadog -f product-service/compose.yaml up -d
docker compose --env-file .env.datadog -f web-ui/compose.yaml up -d
docker compose --env-file .env.datadog -f datadog-compose.yaml up -d
```

Validate with `docker ps`, the customer/product readiness endpoints, and
`curl -I http://127.0.0.1:${WEB_UI_PORT:-80}` before browser testing.

## Full-stack standalone order on t3.large or larger

After stopping existing containers, pulling changes, and validating each file,
build each application while the platform remains stopped. Then start stacks in
dependency order, always as separate Compose commands:

1. `customer-service/compose.yaml`
2. `product-service/compose.yaml`
3. `order-service/compose.yaml`
4. `payment-service/compose.yaml`
5. `network-service/compose.yaml`
6. `fulfillment-service/compose.yaml`
7. `billing-service/compose.yaml`
8. `orchestration-service/compose.yaml` (Kafka + Temporal + PostgreSQL)
9. `tracking-service/compose.yaml` (requires Kafka from step 8)
10. `web-ui/compose.yaml`
11. `admin-ui/compose.yaml` only on a trusted/private host
12. `datadog-compose.yaml`

The orchestration workflow will stay at `WAITING_FOR_PAYMENT` because the
current payment boundary creates only a `PENDING` intent. A provider callback or
operator confirmation path is intentionally not implemented yet.

## Swagger through an SSH tunnel

Keep backend ports private. From the operator workstation, forward the required
loopback port, for example Customer Service:

```bash
ssh -i /absolute/path/to/key.pem -N \
  -L 8080:127.0.0.1:8080 ec2-user@ELASTIC_IP
```

Open `http://localhost:8080/swagger-ui.html`. Substitute ports `8081`,
`8084`–`8088` for other loopback-published services. Order and Payment need a
temporary loopback publication or an in-container/network request because their
current Compose files do not publish `8082`/`8083` to the host.

## Safe cleanup and data warning

Space can be reclaimed with:

```bash
docker builder prune -f
docker image prune -f
```

The current test policy does not create deployment backups. That is acceptable
only while data is disposable. Never run `docker volume prune`,
`docker system prune --volumes`, or `docker compose down --volumes` when data
matters: each service's MongoDB data is stored in a named Docker volume.
