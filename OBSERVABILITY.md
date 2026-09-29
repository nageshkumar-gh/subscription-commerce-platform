# Datadog observability

The Datadog Agent collects infrastructure metrics and selectively collects
Docker logs from Customer Service, its MongoDB database, and the Web UI. Each
container opts in through `com.datadoghq.ad.logs` labels; the Agent does not
collect logs from every container on the host.

## Configure the deployment host

1. In Datadog, open **Integrations > Agent > Docker** and confirm the Datadog
   site for your account.
2. Copy the committed template and edit only the local copy:

   ```bash
   cp .env.datadog.example .env.datadog
   chmod 600 .env.datadog
   ```

3. Set `DD_API_KEY` and `DD_SITE` in `.env.datadog`. Set `APP_VERSION` to an
   image tag or Git commit SHA when one is available. The `.env.datadog` file is
   ignored by Git and must not be committed.

## Deploy

The Customer stack creates the shared Docker network. MongoDB is intentionally
not published on an EC2 host port; Customer Service reaches it by the
`customer-mongodb` service name on that network.

```bash
docker compose --env-file .env.datadog -f customer-service/compose.yaml up -d --build
docker compose --env-file .env.datadog -f web-ui/compose.yaml up -d --build
docker compose --env-file .env.datadog -f datadog-compose.yaml up -d
```

Verify the Agent locally:

```bash
docker ps --filter name=datadog-agent
docker logs --tail 100 datadog-agent
docker exec datadog-agent agent status
```

The Agent status should include a running Logs Agent and the three labelled
containers. EC2 must permit outbound HTTPS traffic on port 443.

## Find logs

In **Datadog > Logs > Explorer**, start with:

```text
env:test (service:customer-service OR service:web-ui OR service:customer-mongodb)
```

Useful narrower searches are:

```text
env:test service:customer-service
env:test service:web-ui
env:test service:customer-mongodb
env:test service:customer-service (status:error OR "ERROR" OR "Exception")
```

Generate test traffic with the service health and Web UI endpoints, then allow
one or two minutes for new logs to appear:

```bash
curl -i http://localhost:8080/actuator/health
curl -I http://localhost:5173
```

## Troubleshoot

- If the Agent fails to start, check `docker logs datadog-agent` for an invalid
  API key or incorrect `DD_SITE`.
- If the Agent runs without application logs, inspect its status and confirm
  the `com.datadoghq` labels exist with
  `docker inspect customer-service --format '{{json .Config.Labels}}'`.
- Recreate application containers after changing labels, then restart the
  Agent. Updating a Compose file does not modify an already-created container.
- Do not enable collect-all unless its ingestion volume and cost are understood.
- Do not paste the Datadog API key into tickets, chat, logs, or source control.

Java APM and trace/log correlation are intentionally a later change. Establish
reliable log collection first, then add the Java tracer and APM intake as a
separately tested deployment.
