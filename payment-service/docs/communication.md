# Service Communication and Integration Guide

## What Exists Now

There is one local orchestration entry point: the repository-root [docker-compose.yml](../../docker-compose.yml). Coffee checkout is integrated with Payments; container startup alone is not a substitute for the order, payment, and fulfillment contracts described below.

| Service | Communicates with | Transport and purpose | Status |
| --- | --- | --- | --- |
| Browser and frontend Nginx | Coffee backend | Same-origin HTTP, session cookie, CSRF; catalog/orders/account | Existing |
| Coffee backend | Coffee PostgreSQL | JDBC; only coffee-owned tables | Existing |
| Coffee backend and browser | Keycloak | OIDC login; backend maintains the customer session | Existing optional SSO |
| Coffee backend | Mailpit or configured SMTP | Committed order emails | Existing |
| Local demo helper | Payments | Bearer JWT over loopback HTTP; create/read synthetic USD payment | Implemented |
| Payments | Its PostgreSQL | JDBC/Flyway; attempts, event inbox, outbox | Implemented |
| Payments | Stripe sandbox | Official Java SDK over HTTPS; create/retrieve Checkout Session | Implemented adapter; real sandbox access still required |
| Stripe or local Stripe CLI | Payments | Raw-body signed webhook; no browser session | Implemented receiver |
| Payments outbox publisher | RabbitMQ | AMQP, persistent messages, mandatory routing, confirms | Implemented |
| RabbitMQ | Demo consumer | Manual acknowledgments; own PostgreSQL transaction and deduplication | Implemented |
| Coffee backend | Payments and RabbitMQ | Authenticated Checkout creation and payment-result consumer | Integrated |

The demo consumer runs from the same image/JAR with `APP_ROLE=consumer` but in a different process with different database credentials. It cannot access the payment tables. The shared build is a POC convenience, not a reason to share tables with future Orders.

## Use Case 1: Standalone Sandbox Payment

1. The helper generates a short-lived JWT with issuer `payment-poc`, audience and authorized party (`azp`) `payment-service`, and subject `local-demo`. It reads the random signing secret from ignored local configuration, never a browser bundle.
2. It calls `POST /internal/payments` with `Idempotency-Key` and a synthetic order reference. Payments validates the signature, issuer, audience, expiry, exact `azp`, USD cents, and caller-scoped request identity.
3. Payments commits a `PENDING` attempt before making any provider call. Stripe receives the stable payment ID as its idempotency key. A timeout leaves a recoverable attempt, not a failed payment or permission to create a second charge.
4. Payments stores the returned session reference/URL and returns a payment view. The user opens Stripe-hosted Checkout; test card entry stays with Stripe.
5. Stripe sends a signed event. The CLI can forward it locally. Payments verifies the exact raw-body signature and timestamp and rejects live-mode events, then persists event/session identifiers before returning 204. Duplicate event IDs are no-ops; database failure returns an error rather than acknowledging lost data.
6. A worker retrieves the session using the sandbox key. Amount, USD currency, session ID, internal payment reference, and order reference must match. A complete paid session can transition to `SUCCEEDED`; a verified expired/unpaid session can transition to `EXPIRED`.
7. In one SQL transaction, Payments changes state and inserts a stable-ID result event into the outbox. If the insert fails, the payment transition rolls back too.
8. The publisher sends that event to RabbitMQ. It marks the row published only after a positive broker confirm with no mandatory return. The demo consumer commits its processed-event record and order update together, then acknowledges the delivery.
9. `GET /internal/payments/PAYMENT_ID` reports payment state to its owning JWT subject. Returning to `/checkout/return` never marks paid and does not expose private payment data.

The caller-selected amount is allowed only for the restricted synthetic-order demonstration. For coffee checkout, the backend calculates the order total; the customer browser cannot select or override the charge amount.

## Use Case 2: Coffee Shop Customer Checkout

The integrated customer flow uses a server-calculated order total and a verified payment-result event to gate fulfillment.

1. The browser sends its coffee/quantity/fulfillment details to the existing backend with its current session and CSRF protection.
2. The backend authorizes ownership, calculates the USD amount from its catalog, snapshots it, and persists an order as `AWAITING_PAYMENT`. It does not enable preparation or send the order confirmation until payment is verified.
3. After committing the order, the backend obtains a Keycloak client-credentials token for Payments. It calls `http://payments:18081/internal/payments` with a stable checkout key, order reference, and server-calculated USD cents. There is no SQL transaction spanning both services.
4. The Keycloak fixture defines a separate confidential `payment-service` workload client. `node scripts/keycloak-local.mjs up` generates its secret separately from the customer OIDC client, attaches the `payments-workload` client scope with the payment audience mapper, and starts the integrated profiles. The coffee backend obtains a client-credentials token; Payments validates issuer/audience with Keycloak JWKS and requires the exact `azp=payment-service`. The local HMAC demo identity remains only for the isolated payment CLI and is not the backend service identity.
5. The backend saves the payment ID and returns the hosted URL. If the call times out, it retries the same order/key and returns a recoverable pending response instead of creating another order/payment.
6. Payments verifies Stripe independently, then emits a result through its outbox. The backend-owned queue bound to `payments.events` delivers the event to Orders. The real backend does not compete on the demo queue; independent subscribers need their own queues.
7. Orders validates the schema, payment/order association, amount, and currency against its own snapshot. In one local transaction it deduplicates the event and transitions the order to `PAID`; after commit, the confirmation email is sent. It acknowledges only after commit.
8. The browser returns to the coffee application's order-status route and reads Orders, not private payment tables. Orders can briefly remain pending until the result arrives. Browser-supplied success flags are ignored.

If the backend is down, its durable queue retains results. If a late success conflicts with a canceled order or another payment, park it for explicit reconciliation; do not fulfill twice or pretend the money never arrived. Refund and cancellation policy must be implemented separately before live processing.

## Synchronous Versus Asynchronous

Use synchronous HTTP where the caller needs a direct answer: create/reuse Checkout and read current payment status. Use asynchronous events for durable facts: a verified payment succeeded or expired. Payment events are not commands to trust arbitrary amounts, and delivery of an event does not replace consumer-side ownership/amount validation.

Container-to-container addresses use Compose DNS and container ports: `payments:18081`, `rabbitmq:5672`, `payments-db:5432`, and `demo-db:5432`. Host tools use published loopback ports: 18081, 15673, 15433, and 15434 respectively. `localhost` inside one container never means another container. Deployed service/API and database/broker traffic needs TLS and network policy; loopback HTTP/plain AMQP here is a local-only simplification.

## API and Event Contracts

Internal create/read endpoints require a valid issuer, audience, subject, expiry, and exact `azp=payment-service`. This dedicated confidential Keycloak client is the service identity; its default `payments-workload` client scope adds the Payment audience. No browser token is forwarded. JWT subjects isolate payment reads and request keys. `Idempotency-Key` and order references accept 1-100 ASCII letters, digits, underscores, or hyphens.

```json
{
  "orderReference": "demo-order-1",
  "amountMinor": 300,
  "currency": "USD"
}
```

Amounts are integer cents, currently constrained to 50-100000 for a small sandbox demonstration. A repeated identical request returns the same attempt. Changed payloads or a new key for the same caller/order return 409. One attempt per demo order is deliberately conservative; use a new synthetic reference for another demonstration. An unresolved session creation older than 23 hours requires review rather than blindly retrying an idempotency key Stripe may have pruned.

A result is persisted once with a stable event ID and message ID. The exchange routing key equals `eventType`:

```json
{
  "schemaVersion": 1,
  "eventId": "86f13508-4caa-47d0-931e-cf0f0dc46e32",
  "eventType": "payment.succeeded",
  "paymentId": "bc6c3f9b-ee47-416b-9893-105007e5e7a4",
  "orderReference": "demo-order-1",
  "amountMinor": 300,
  "currency": "USD",
  "status": "SUCCEEDED"
}
```

`payment.expired` carries `EXPIRED`. There is no terminal `FAILED` event yet: a declined card can be retried in the same open session. The demo consumer records `PREPARATION_ALLOWED` or `NOT_PAID`; it does not prepare a real coffee or send a customer email. Contract version 1 is validated, and reusing an event ID with a different payload is rejected.

## RabbitMQ Delivery Mechanics

| Resource | Purpose |
| --- | --- |
| `payments.events` | Durable topic exchange; payment-result publisher |
| `demo.orders.payments` | Durable demo queue bound to `payment.*` |
| `payments.retry` | Durable direct exchange for transient consumer failures |
| `demo.orders.retry` | Durable queue with 3-second TTL, dead-lettering back to `payment.retry` |
| `payments.dead` | Durable dead-letter exchange |
| `demo.orders.dead` | Poison/conflicting/exhausted-message review queue |

Publisher confirms mean the broker accepted publication; they do not prove a consumer committed anything. Mandatory returns detect an exchange that could not route the message. Consumer acknowledgments mean processing committed in the consumer's database. Those are different guarantees.

A crash after publication but before the outbox update causes the same event to be published again. A crash after consumer commit but before acknowledgment causes redelivery. The unique processed-event ID and order update share a transaction, making these deliveries logically idempotent. This is at-least-once delivery, not exactly-once messaging.

Transient database failures publish a delayed retry with a retry counter, confirm it, then acknowledge the original. After three delayed retries, the consumer rejects without requeue and RabbitMQ dead-letters it. Invalid/conflicting events dead-letter immediately. If retry-copy publication itself fails, the listener's non-requeue policy parks the original in the dead-letter queue for review rather than looping indefinitely.

The local broker is a single node with classic durable queues, not a highly available production cluster. TTL/DLX transfer in classic queues does not have end-to-end lossless guarantees during broker/node failures. Production should evaluate quorum queues, at-least-once dead-lettering policies, per-service broker ACLs/vhosts, TLS, monitoring, and a tested replay policy. No auto-replay of dead letters is configured.

## Failure and Recovery Exercises

Run tests with `node payment-service/scripts/local.mjs test-infra` from the repository root. They cover real Postgres/RabbitMQ routing, delayed retries, duplicate conflicts, dead letters, and unroutable mandatory publications; the Stripe boundary is mocked.

For manual exercises, use the same `--env-file` options as the README when invoking Compose. Commands below assume your shell is configured with the payment environment files.

1. Stop `rabbitmq`, then complete a genuine sandbox payment. Inspect the payment/outbox: the payment remains saved while its result is unpublished. Start the broker and observe publication/consumption resume. Automated outbox recovery tests simulate this boundary without stopping a shared broker.
2. Stop `demo-consumer`, complete a sandbox payment, and inspect queue depth in the management UI. Start the consumer and observe its own database catch up.
3. Repeat the same checkout request. It must return the same attempt, not another charge. Change its amount and confirm 409.
4. Close/cancel hosted Checkout. It remains pending until provider state establishes paid or expired. The redirect alone never changes state.
5. Inspect dead letters for malformed messages. Review the cause and schema before any deliberate replay; do not delete consumer deduplication records to force reprocessing.

Useful non-secret database inspection commands from the root:

```sh
docker compose --profile payments exec -T payments-db psql -U payments -d payments -c 'SELECT id,order_reference,status,amount_minor,currency FROM payment_attempts;'
docker compose --profile payments exec -T payments-db psql -U payments -d payments -c 'SELECT event_id,payment_id,published FROM payment_outbox;'
docker compose --profile payments exec -T demo-db psql -U demo_orders -d demo_orders -c 'TABLE demo_orders;'
```

Webhook and outbox workers use bounded batches of 20 and fixed delays (10 seconds); reconciliation runs every 60 seconds. This initial POC does not implement adaptive backoff, parallel work leasing, operational repair endpoints, inbox/outbox retention, or a complete late-payment/refund workflow. Keep demonstrations small; production hardening must address worker fairness and provider rate-limit backoff.

## Database and Production Boundaries

Separate databases/credentials are the ownership rule; separate PostgreSQL containers are a local failure-isolation choice. Production may use separate managed instances or isolated databases on a managed cluster. Never join another service's tables or share unrestricted SQL credentials to bypass an API/event contract.

The official local Postgres images bootstrap their database owner users; they are not a complete production least-privilege setup. Production needs distinct migration/runtime roles, restricted network access, TLS, managed secrets and rotation, tested backups/recovery, metrics/alerts, and payment-audit retention. Creating more containers alone supplies none of those controls.

The helper's `.env` is mode 600 and ignored by Git and Docker builds. HMAC demo JWTs are a local test identity, not a production Keycloak client. Stripe objects/events must be sandbox-only. A signed mismatch is quarantined in `webhook_inbox`; status reconciliation logs unresolved cases without declaring success. No real merchant eligibility, funds, or USD settlement in Cameroon is claimed.