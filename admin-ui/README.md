# Admin UI

The operations dashboard reads real orders and the tracking-service summary API, then reports health for order, orchestration, tracking, payment, network, fulfillment, and billing services. A failed health check is shown independently and does not hide available order tracking data.

```bash
docker compose -f admin-ui/compose.yaml up -d --build
```

The UI defaults to host port `5174`. Nginx keeps backend services private and proxies both API and health requests over the shared `subscription-platform` network. For local development, run `npm install` and `npm run dev`.

Authentication is intentionally not implemented yet. Do not expose this operations UI publicly until an identity provider and role-based access are added.
