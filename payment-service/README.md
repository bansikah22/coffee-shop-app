# Payment Service

Standalone sandbox implementation. The coffee backend and frontend are not yet connected to Payments. All services are declared in the root [docker-compose.yml](../docker-compose.yml); there is no second payment Compose file.

## Purpose

A Java/Spring Boot service demonstrating one-time USD payments through Stripe sandbox hosted Checkout, durable webhook receipt, a transactional outbox, RabbitMQ, and an independent demo order consumer. It does not share coffee tables or move real money.

## Initial Direction

- Language/framework: Java with Spring Boot, matching the existing backend.
- Currency: USD, preserving current coffee pricing. Provider-facing amounts use integer cents: $3.00 is 300 cents. No implicit XAF conversion.
- Provider: official Stripe Java SDK 33.0.0, whose pinned API version is `2026-05-27.dahlia`. Genuine sandbox access and secrets are needed only for the interactive Stripe demonstration; automated tests mock the provider boundary.
- Checkout: Stripe-hosted Checkout in one-time payment mode with test cards only. MTN Mobile Money and Orange Money are deferred.
- Environment: sandbox only. No live credentials, real charges, or payouts. Sandbox access does not establish merchant eligibility or USD settlement support in Cameroon.
- Payment authority: a verified provider status, never the browser redirect.
- Order integration: deferred. A separate process runs the demo consumer using the same image with `APP_ROLE=consumer`. It consumes payment events into its own database. It is not the coffee backend.

## Run and Test

Prerequisites: Java 25, Maven, Node.js 26, and Docker Compose. From this directory:

```sh
node scripts/local.mjs init
node --test scripts/sandbox-key.test.mjs
mvn test
node scripts/local.mjs test-infra
```

`init` creates or preserves random local credentials in ignored `.env` with mode 600. `test-infra` starts only payment databases and RabbitMQ through the root Compose file, then runs all tests against real PostgreSQL/RabbitMQ. Stripe is mocked; no provider account or real funds are used. Plain `mvn test` runs fast H2/mock tests and explicitly skips the real-infrastructure class.

For account creation, sandbox keys, CLI installation/login, webhook secrets, and a first test payment, follow the [Stripe sandbox setup guide](docs/stripe-sandbox.md).

Sandbox secret-key guards accept `sk_test_`, `rk_test_`, and temporary `rkcs_test_` keys; provider permissions and expiry still apply. `whsec_` is the signing-secret prefix, not a webhook URL. Do not share the private environment file in chat or screenshots.

For interactive Checkout, enter your sandbox `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` directly in this directory's ignored `.env`. Do not paste them into chat or Git. Obtain the local endpoint secret using Stripe CLI in a separate terminal:

```sh
stripe listen --events checkout.session.completed,checkout.session.expired --forward-to localhost:18081/webhooks/stripe
```

Set the CLI's signing secret in `.env`, keep that forwarding process running, then:

```sh
node scripts/local.mjs up
node scripts/local.mjs checkout demo-order-1 300
node scripts/local.mjs status PAYMENT_ID
```

The `up` helper runs Maven verification/packaging before building the runtime image, matching the coffee backend's prebuilt-JAR convention. Docker does not repeat Maven dependency resolution. If invoking root Compose directly, first run `mvn -f payment-service/pom.xml verify` (and the existing backend packaging step if rebuilding the coffee backend).

Open the returned provider checkout URL and use Stripe's documented test cards only. The helper generates five-minute local demo JWTs internally; it does not print them. Repeating the same order and amount reuses its attempt; changing the amount or request key for an existing order is rejected. New demonstrations need a new synthetic order reference.

RabbitMQ management is at `http://localhost:15674`; use the generated RabbitMQ credentials from the ignored `.env`. The payment API is on `http://localhost:18081`, and the demo consumer's health endpoint is on `http://localhost:18082/actuator/health`. Host ports are loopback-only.

`node scripts/local.mjs down` stops only payment services, keeping volumes and the coffee stack intact. From the repository root, starting everything together is also possible after credentials/build prerequisites are ready:

```sh
KEYCLOAK_ENABLED=true docker compose --env-file .env --env-file payment-service/.env --profile sso --profile payments up -d --build
```

The root `.env` is the existing coffee/SSO configuration; omit its `--env-file` option if it does not exist. The payment helper loads it automatically when present. The `sso` profile starts Keycloak but does not enable backend SSO by itself: the explicit `KEYCLOAK_ENABLED=true` override is required because the existing SSO preparation script deliberately saves a disabled default in `.env`. Profile `payments-infra` enables only databases/broker; `payments` also enables the API and demo consumer. Explicit service selections in the helper avoid starting or rebuilding the coffee app. Do not use `down --volumes` unless you intend to erase data. Initialization passwords do not rotate an already-initialized PostgreSQL/RabbitMQ volume.

## Architecture

Start with the [visual target architecture](../.github/modernize/assessment/engines/facts/architecture-diagram.md): an application overview and internal payment-component diagram, both explicitly proposed rather than deployed.

See [docs/architecture.md](docs/architecture.md) for ownership boundaries, payment flow, security controls, provider caveats, and decisions still open.

See [docs/communication.md](docs/communication.md) for use-case flows, API/event contracts, queue semantics, SQL inspection commands, failure exercises, backend-integration steps, and production boundaries.

## Java Layout

```text
src/main/java/io/github/tandapnoelbansikah/payment/
  api/                 Internal payment API and public provider webhook boundary
  application/         Payment use cases and orchestration
  configuration/       Spring and security configuration
  domain/              Payment and attempt state, transitions, and value types
  messaging/            Outbox and payment-result event integration
  persistence/          Payment records, provider events, and outbox storage
  provider/stripe/     Stripe SDK adapter and provider response mapping
  demo/                Independently run demo order consumer and its database transactions
src/main/resources/     Configuration templates and database migrations
src/test/java/io/github/tandapnoelbansikah/payment/
  api/                 API and webhook tests
  application/         Payment flow tests
  domain/              State-transition tests
  provider/stripe/     Sandbox adapter contract and webhook tests
```

Meaningful comments explain transaction ordering, provider timeout recovery, broker confirms versus routing returns, and acknowledgment after database commit. The demo's PostgreSQL users own isolated instances; production still needs restricted runtime versus migration roles, TLS, operational monitoring, retention, and tested backups. This POC is not production payment certification.
