# Subscription Commerce Web UI

React, TypeScript, and Vite frontend for the Subscription Commerce Platform. This version uses an in-memory mock API and supports one mobile phone with one eSIM plan in the cart at a time.

## Requirements

- Node.js 20.19+ or 22.12+
- npm

## Setup

```bash
cd web-ui
npm install
npm run dev
```

Open `http://localhost:5173`. Vite routes customer, product/eSIM, order, and payment API paths to ports 8080, 8081, 8082, and 8083 respectively.

Customer registration now calls the Spring Boot customer service. Start MongoDB and the service in another terminal before registering:

```bash
cd customer-service
./mvnw spring-boot:run
```

The customer service listens on `http://localhost:8080` and stores registrations in the `customer_db.customers` MongoDB collection. Login remains simulated until an authentication endpoint is added.

## Quality checks

```bash
npm run lint
npm test
npm run build
```

## Demo behavior

- Registration and login accept locally validated details.
- `blocked@example.com` demonstrates an unauthorized login response.
- `server@example.com` demonstrates a server error on registration or login.
- Payment is simulated. Any 16-digit test card number is accepted; no payment data is transmitted or stored.
- Mock data and operations are exposed through `src/api/client.ts`, keeping pages independent of the eventual Spring Boot APIs.

Authentication, cart selection, and completed subscriptions are intentionally held in memory and reset when the browser is refreshed.
