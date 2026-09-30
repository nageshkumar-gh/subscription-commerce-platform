# EC2 single-host deployment runbook

Deploys the whole platform with Docker Compose on one EC2 host from the repository
root. This is a test environment: a single host is a single point of failure, and
data lives only in local Docker volumes (no backups).

## Host size and network

- **Instance:** t3.xlarge (4 vCPU, 16 GiB) recommended, t3.large (8 GiB) minimum,
  with at least 30 GiB gp3. The stack is 25 containers (12 services, 8 MongoDB,
  2 PostgreSQL, Kafka, Temporal, Datadog agent) and uses about 4 GiB when idle.
  On 4 GiB hosts (t2/t3.medium) builds stall and Temporal calls time out.
- **Elastic IP:** attach one so the public address and `CORS_ALLOWED_ORIGINS`
  survive stop/start and instance resizes.
- **Security group inbound:** `22` from your IP only, `80` from anywhere (storefront).
  Nothing else. Backends and databases are published on `127.0.0.1` only, and the
  admin console listens on `127.0.0.1:5174` and is reached through an SSH tunnel.

## One-time host preparation (Amazon Linux 2023)

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user        # log out and back in
# Docker Compose v2 plugin, if `docker compose version` fails:
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

git clone https://github.com/nageshkumar-gh/subscription-commerce-platform.git
cd subscription-commerce-platform
```

## Configuration

Create the server's env file from the template. It is git-ignored; never commit it.

```bash
cp .env.datadog.example .env.datadog
chmod 600 .env.datadog
sed -i "s|^AUTH_JWT_SECRET=.*|AUTH_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env.datadog
vim .env.datadog     # DD_API_KEY, CORS_ALLOWED_ORIGINS=http://<ELASTIC_IP>
```

| Key | Purpose |
|---|---|
| `AUTH_JWT_SECRET` | Signs and verifies login tokens. customer-service, product-service and orchestration-service will not start without it. Generate it on the host, never reuse one from git history. |
| `CORS_ALLOWED_ORIGINS` | Storefront origin, e.g. `http://<ELASTIC_IP>` |
| `ADMIN_UI_PORT` | `127.0.0.1:5174`: admin console bound to loopback only |
| `ADMIN_CORS_ALLOWED_ORIGINS` | `http://localhost:5174,http://127.0.0.1:5174`, the admin console's origin through the tunnel |
| `AGENT_CUSTOMER_IDS` | Accounts allowed into the admin console (set after registering, see below) |
| `CATALOG_ADMIN_CUSTOMER_IDS` | Accounts allowed to edit the catalogue (optional) |
| `DD_*`, `APP_VERSION` | Datadog agent and tags |

Check every stack resolves before building:

```bash
for s in customer-service product-service order-service payment-service network-service fulfillment-service \
         billing-service orchestration-service tracking-service invoice-service web-ui admin-ui; do
  docker compose --env-file .env.datadog -f $s/compose.yaml config --quiet && echo "ok $s"
done
```

## Deploy

Always build **one stack at a time**. `scripts/deploy-all.sh` does this in dependency
order (customer, product, order, payment, network, fulfillment, billing,
orchestration with Kafka/Temporal/PostgreSQL, tracking, invoice with PostgreSQL,
web UI, admin UI) and creates the `subscription-platform` network if needed.
Run it under `nohup` so a dropped SSH session does not stop it:

```bash
nohup ./scripts/deploy-all.sh > ~/deploy.log 2>&1 &
tail -f ~/deploy.log              # one line per stack, then DONE (about 10 minutes on t3.xlarge)
docker compose --env-file .env.datadog -f datadog-compose.yaml up -d   # optional Datadog agent
```

Never merge compose files (`-f a.yaml -f b.yaml`): each file's `build: .` is relative
to its own folder. Run each one as its own command from the repository root.

Verify:

```bash
docker ps --format '{{.Names}}\t{{.Status}}'                      # all Up / healthy
curl -s -o /dev/null -w '%{http_code}\n' http://<ELASTIC_IP>/                 # 200
curl -s -o /dev/null -w '%{http_code}\n' http://<ELASTIC_IP>/api/me/orders    # 401 without a login
```

## Admin console and agent accounts

The admin console is not published publicly. From your workstation:

```bash
ssh -i ~/.ssh/<key>.pem -N -L 5174:127.0.0.1:5174 ec2-user@<ELASTIC_IP>
```

then open `http://localhost:5174`. "Connection refused" lines in that terminal mean
admin-ui is not running yet.

Every API call from the console needs an operations agent's token. To create an agent:

1. Register an account on the storefront, `http://<ELASTIC_IP>/register`.
2. Look up its ID:
   ```bash
   docker exec customer-mongodb mongosh --quiet customer_db \
     --eval 'print(db.customers.findOne({email:"agent@example.com"})._id.toString())'
   ```
3. Add it to `AGENT_CUSTOMER_IDS` in `.env.datadog` (comma-separated for several agents).
4. Recreate customer-service:
   ```bash
   docker compose --env-file .env.datadog -f customer-service/compose.yaml up -d --no-deps customer-service
   ```
5. Sign in to the console. Removing an ID revokes access once the agent's current token expires (1 hour).

## Updating to a new version

`main` only changes through pull requests with green CI. On the host:

```bash
cd ~/subscription-commerce-platform
git fetch origin && git merge --ff-only origin/main
# Rebuild only the stacks that changed, one at a time:
docker compose --env-file .env.datadog -f <stack>/compose.yaml up -d --build --no-deps <service>
# or everything:
nohup ./scripts/deploy-all.sh > ~/deploy.log 2>&1 &
```

New keys added to `.env.datadog.example` must be copied into `.env.datadog` first,
otherwise the stack that needs them fails at `config` or at startup.

## Order flow check

1. On the storefront, register a customer and place an order.
2. In the admin console: **Payments**: approve. **Delivery**: advance to delivered.
   **Activation**: activate. **Billing**: start billing.
3. The storefront's **My orders** shows the order completed and **My subscription**
   lists it. **Invoicing** runs daily at 02:00 Europe/Dublin, or on demand.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Builds hang, `docker` commands take minutes, Temporal `DEADLINE_EXCEEDED` | Host out of memory: resize (see Host size) and build one stack at a time |
| A service exits with `AUTH_JWT_SECRET must be set` | Key missing from `.env.datadog`, or `--env-file` was not passed |
| Admin sign-in fails: `403 Invalid CORS request` | Admin origin missing from `ADMIN_CORS_ALLOWED_ORIGINS` |
| Admin sign-in: "This account is not an operations agent" | ID not in `AGENT_CUSTOMER_IDS`, or customer-service not recreated after changing it |
| Admin console shows 502 for one tab | That backend is stopped; the other tabs keep working |

## Swagger through an SSH tunnel

Backend ports are loopback-only. Forward the one you need, for example customer-service:

```bash
ssh -i ~/.ssh/<key>.pem -N -L 8080:127.0.0.1:8080 ec2-user@<ELASTIC_IP>
```

and open `http://localhost:8080/swagger-ui.html`. Loopback ports: customer 8080,
product 8081, network 8084, fulfillment 8085, billing 8086, orchestration 8087,
tracking 8088, invoice 8089. MongoDB is on 27017–27024 and the invoice PostgreSQL on
5433, also through a tunnel. Order (8082) and payment (8083) are not published on the
host; reach them from inside the Docker network, e.g.
`docker exec orchestration-service curl -s http://order-service:8082/api/orders`.

## Data safety

No backups are taken; this is acceptable only while data is disposable. Space can be
reclaimed with `docker builder prune -f` and `docker image prune -f`. Never run
`docker volume prune`, `docker system prune --volumes` or `docker compose down --volumes`
when the data matters: every database lives in a named Docker volume.
