# Payment Service

Status: architecture and directory skeleton only. This directory does not yet contain a Spring Boot build, application code, Dockerfile, provider credentials, or deployment configuration.

## Purpose

A future independently deployable Spring Boot service for creating and tracking Cameroon mobile-money payment attempts. It will own payment state and provider integration; it will not share the order service's database.

## Initial Direction

- Runtime: Spring Boot, matching the existing Java backend.
- Market: Cameroon; XAF is the candidate settlement currency and must be confirmed before implementation.
- Provider candidate: Fapshi, whose documentation describes MTN Mobile Money and Orange Money collections.
- Checkout: prefer a hosted payment link for the first iteration, after verifying in sandbox that customers can select both supported methods.
- Payment authority: a verified provider status, never the browser redirect.
- Order integration: deferred. The future contract should use authenticated service-to-service requests and payment-result events; no connection to the current backend is implemented here.

## Architecture

See [docs/architecture.md](docs/architecture.md) for ownership boundaries, payment flow, security controls, provider caveats, and decisions still open.

## Planned Java Layout

```text
src/main/java/io/github/tandapnoelbansikah/payment/
  api/                 Internal payment API and public provider webhook boundary
  application/         Payment use cases and orchestration
  configuration/       Spring and security configuration
  domain/              Payment and attempt state, transitions, and value types
  messaging/            Outbox and payment-result event integration
  persistence/          Payment records, provider events, and outbox storage
  provider/fapshi/     Fapshi adapter and provider response mapping
src/main/resources/     Configuration templates and database migrations
src/test/java/io/github/tandapnoelbansikah/payment/
  api/                 API and webhook tests
  application/         Payment flow tests
  domain/              State-transition tests
  provider/fapshi/     Provider adapter contract tests
```

The package directories are intentionally empty placeholders. Add implementation and build configuration only after the open decisions in the architecture document are resolved.
