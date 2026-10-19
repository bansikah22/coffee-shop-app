# Coffee Shop

Coffee Shop is a React and Spring Boot app for browsing coffee, ordering, and tracking pickup or delivery. PostgreSQL stores application data; paid orders are confirmed through Stripe-hosted Checkout before fulfillment.

## Demo

Watch the [Coffee Shop demo video](docs/demo-video.mov) (QuickTime, 128 MB).

## Architecture

The browser reaches the API through Nginx. The backend owns order totals and state; Payments owns Stripe interactions and publishes verified results through RabbitMQ.
# Coffee Shop

Coffee Shop is a React and Spring Boot app for browsing coffee, ordering, and tracking pickup or delivery. PostgreSQL stores application data; paid orders are confirmed through Stripe-hosted Checkout before fulfillment.

## Demo

Watch the [Coffee Shop demo video](docs/demo-video.mov) (QuickTime, 128 MB).

## Architecture

Nginx serves the React app and proxies API requests to the coffee backend. The backend owns order totals and state; Payments owns Stripe interactions and publishes verified results asynchronously.

```mermaid
flowchart LR
    Customer[Customer] --> Browser[React app]
    Nginx[Nginx] -->|serves UI| Browser
    Browser -->|same-origin session and CSRF| Nginx
    Nginx -->|API proxy| Orders[Coffee backend]
    Orders -->|save awaiting-payment order| OrdersDB[(Coffee PostgreSQL)]

    Orders -->|workload token, amount, idempotency key| Payments[Payments service]
    Payments -->|create hosted session| Stripe[Stripe Checkout]
    Stripe -->|checkout URL| Payments
    Payments -->|checkout URL| Orders
    Orders -->|checkout URL| Browser
    Browser -->|hosted card entry| Stripe
    Stripe -.->|return URL only; not payment proof| Browser
    Stripe -->|signed webhook| Payments
    Payments -->|verify status; commit attempt and outbox| PaymentsDB[(Payments PostgreSQL)]
    PaymentsDB -->|pending event| Publisher[Outbox publisher]
    Publisher -->|confirmed payment event| RabbitMQ[RabbitMQ]
    RabbitMQ -->|payment result on Orders-owned queue| Orders
    Orders -->|deduplicate and mark paid| OrdersDB
    Orders -->|after-commit confirmation| Mail[Mailpit or SMTP]

    Browser -->|OIDC sign-in| Keycloak[Keycloak]
    Keycloak -->|OIDC callback| Browser
    Orders -->|client credentials| Keycloak
    Keycloak -->|workload token| Orders
```

See the [application flow](docs/architecture/application-flow.svg), [customer journey](docs/architecture/customer-journey.svg), and [payment communication guide](payment-service/docs/communication.md).

## Technology Choices

| Technology | Role and rationale | Alternatives |
| --- | --- | --- |
| React, TypeScript, Vite | Typed customer/admin UI with a quick build loop. | Vue, Angular. |
| Java, Spring Boot | API, security, order rules, and payment integration. | Quarkus, Micronaut. |
| PostgreSQL, Flyway | Relational transactions and versioned schema changes; each service owns its data. | MySQL/MariaDB, Liquibase. |
| Keycloak, OpenID Connect | Optional local SSO and workload identity using standard OIDC. | Entra ID, Auth0, Okta. |
| RabbitMQ | Durable asynchronous payment events with routing, acknowledgements, and retries. | Azure Service Bus, Kafka. |
| Stripe Checkout | Hosted card entry keeps card data out of this app. | Adyen, Braintree, PayPal, subject to availability. |
| Nginx, Mailpit | Same-origin proxy and safe local email capture. | Caddy/Traefik; smtp4dev or hosted SMTP. |

## Run Locally

Prerequisites: Docker Compose, Java 25, Maven, Node.js 26, and npm.

```sh
cd backend
./mvnw package -DskipTests
cd ..
docker compose up --build --detach
```

Open `http://localhost:8088`. For integrated Keycloak, Stripe sandbox, RabbitMQ, and Mailpit setup, see [docs/keycloak.md](docs/keycloak.md) and the [Stripe sandbox guide](payment-service/docs/stripe-sandbox.md). Stop with `docker compose down`; add `--volumes` only to delete local database data.

## CI/CD

GitLab is the source of truth. GitLab CI validates and publishes changed components; GitHub Actions verifies changes. The manual GitLab-to-GitHub sync is documented in [docs/github-sync.md](docs/github-sync.md).
