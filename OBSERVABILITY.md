# Datadog observability

The Datadog Agent reads Docker infrastructure metrics and only the logs opted in
through each container's `com.datadoghq.ad.logs` label. Do not enable collect-all
without first reviewing ingestion volume and cost.

## Configure and start

Create `.env.datadog` from the committed template, set `DD_API_KEY`, `DD_SITE`,
`DD_ENV`, and `APP_VERSION`, then protect it with mode `600`. The file is ignored
by Git and must never be committed or pasted into tickets/chat.

Follow the stop/build/start policy in [DEPLOYMENT_RUNBOOK.md](DEPLOYMENT_RUNBOOK.md).
Start the Agent last:

```bash
docker compose --env-file .env.datadog -f datadog-compose.yaml up -d
docker logs --tail 100 datadog-agent
docker exec datadog-agent agent status
```

EC2 needs outbound HTTPS/443. Datadog ports `8125` and `8126` do not need public
security-group rules. Java APM/trace correlation is not enabled yet.

## Log Explorer queries

All application services:

```text
env:test (service:customer-service OR service:product-service OR service:order-service OR service:payment-service OR service:network-service OR service:fulfillment-service OR service:billing-service OR service:orchestration-service OR service:tracking-service)
```

UIs and infrastructure dependencies:

```text
env:test (service:web-ui OR service:admin-ui OR service:kafka OR service:customer-mongodb OR service:product-mongodb OR service:order-mongodb OR service:payment-mongodb OR service:network-mongodb OR service:fulfillment-mongodb OR service:billing-mongodb OR service:tracking-mongodb)
```

Useful focused queries:

```text
env:test service:customer-service (status:error OR "ERROR" OR "Exception")
env:test service:product-service (status:error OR "ERROR" OR "Exception")
env:test service:orchestration-service "WAITING_FOR_PAYMENT"
env:test service:tracking-service ("DLT" OR "Unsupported lifecycle event")
```

MongoDB connection-open/close records with severity `I` are informational, not
application failures. Use structured severity/status filters rather than
alerting on every network message.

## Verification and troubleshooting

- Agent healthy but logs absent: run `agent status`, then inspect the target
  container labels. Recreate a container after label changes.
- One service missing: confirm that service is running and writing to Docker
  stdout/stderr; labels are selective.
- Authentication failure: confirm the API key and the account-specific
  `DD_SITE` (for example `us5.datadoghq.com`).
- No workflow events: Kafka and Temporal come from
  `orchestration-service/compose.yaml`; Tracking also requires that Kafka.
- Workflow appears stalled: `WAITING_FOR_PAYMENT` is expected until an external
  provider/operator changes the payment from `PENDING` to `COMPLETED`; that
  confirmation integration is not implemented yet.

Recommended first monitors are application error rate, missing containers,
host memory/disk pressure, Kafka/Temporal availability, MongoDB health, and
tracking dead-letter activity.
