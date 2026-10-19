# Local Keycloak SSO

This optional local-only setup runs Keycloak `26.8.1` with a separate PostgreSQL database and imports the `coffee-shop` realm. The default Compose stack does not start Keycloak, and the backend keeps `KEYCLOAK_ENABLED=false` unless explicitly enabled.

## Start locally

From `coffee-shop-app`, run the setup command. It creates or updates the ignored `.env`, generates any missing client, admin, and Keycloak database secrets, renders the realm with the matching confidential-client secret, starts the stack, then synchronizes registration settings into the existing realm without deleting its users or data:

```sh
node scripts/keycloak-local.mjs up
```

The command uses Node's cryptographic random generator, stores the private `.env` with mode `600`, and does not print secrets. Existing non-empty secrets are reused, so rerunning the command does not rotate credentials or lock the Keycloak database out of its password. It also preserves unrelated `.env` settings. SSO is enabled only in the `up` command's Compose environment; ordinary `docker compose up` continues to run the local-login app without expecting a Keycloak service. The rendered realm stays under ignored `.local/keycloak/`.

The lifecycle commands are:

```sh
node scripts/keycloak-local.mjs prepare # generate secrets and render realm only
node scripts/keycloak-local.mjs up      # prepare and start/rebuild the stack
node scripts/keycloak-local.mjs status  # prepare and show Compose status
node scripts/keycloak-local.mjs down    # stop containers, keep volumes and secrets
```

Open `http://localhost:8088` and choose **Create account**. With SSO enabled, the registration page leads to Keycloak; select **Register**, create an account with an email address, then open `http://localhost:8025` for the verification message and follow its link. On first successful Keycloak sign-in, the app creates the corresponding customer account automatically. The local setup enables order confirmations and configures unauthenticated SMTP to Mailpit on port `1025`; after Stripe reports a successful sandbox payment, the confirmation appears at `http://localhost:8025`. In the admin workspace, **Ready for collection** changes a paid pickup order to ready and sends a second email once; repeating the action does not send duplicates. Mail delivery failures do not undo a saved order or status update. Keycloak is available at `http://localhost:8180`; its admin username and generated password are in the ignored `.env`. To delete local Keycloak and application data, run `docker compose --profile sso down --volumes` explicitly.

The Keycloak service uses the pinned official release image `quay.io/keycloak/keycloak:26.8.1`, starts in development mode, imports the ignored `.local/keycloak/coffee-shop-realm.json` rendered from `keycloak/coffee-shop-realm.json`, and binds its port to loopback only. The realm allows self-registration, uses the email address as the username, requires email verification, and enables password reset. Mailpit `1.31.6` captures local mail at `http://localhost:8025`; it is only for development and does not deliver messages externally. Its fixture users are intentionally separate from the local application accounts:

| Role | Email | Password |
| --- | --- | --- |
| Admin | `sso-admin@example.com` | `LocalSsoAdmin123!` |
| Customer | `sso-customer@example.com` | `LocalSsoCustomer123!` |

These predictable fixture credentials and `start-dev` are strictly for disposable local development. Do not expose this Keycloak instance or reuse these users or credentials outside a developer machine. Realm import initializes a new realm; edits to the JSON do not overwrite an already-imported realm in the persistent database. The `up` command applies the local registration and mail settings to an existing realm through the Keycloak Admin API without recreating users. For a disposable local reset, stop Compose with `--volumes` and start the provider again.

## Login theme

The local realm uses the `ember-bean` child theme in `keycloak/themes/ember-bean`. It inherits Keycloak 26.8's `keycloak.v2` templates and customizes only the styling: the supplied `frontend/src/assets/login-backgroup.png`, Ember & Bean colors, and a responsive photo/header plus sign-in panel. The theme directory is mounted read-only into the Keycloak container, and the local setup command sets the persistent realm's `loginTheme` to `ember-bean`. Login, registration, verification, and password-reset flows continue to use Keycloak's built-in templates.

## OIDC and roles

The `coffee-shop` client is confidential and uses the authorization-code grant with S256 PKCE. Implicit flow and the resource-owner-password/direct-access grant are disabled. Its sole redirect URI is `http://localhost:8088/api/login/oauth2/code/keycloak`; the public application URL must remain `http://localhost:8088` unless the redirect URI is changed to match.

The browser reaches Keycloak through the public issuer `http://localhost:8180/realms/coffee-shop`. The backend validates the issuer against that public URL while using `http://keycloak:8080/realms/coffee-shop` for token, JWK, and user-info calls over the Compose network. The authorization endpoint and RP logout redirect are browser-facing. These public and internal issuer values are both explicit in Compose.

The ID token receives a signed, multivalued `coffee_shop_roles` claim from the client-role mapper on the dedicated default `coffee-shop-roles` scope. The mapper is restricted to the `coffee-shop` client and exposes only that client's roles. `fullScopeAllowed` is false, and the scope has explicit mappings for `ADMIN` and `CUSTOMER`; user assignments are client roles, not realm roles. A separate default `coffee-shop-identity` scope maps `email` and `email_verified` into the ID token, so the fixture does not depend on Keycloak's built-in `profile` or `email` scopes being imported. Spring requests only the `openid` scope. New users with no admin role are provisioned as customers after verified sign-in. Admin access must be granted explicitly by an administrator. Do not link an SSO identity to an existing local account by reusing its email.

The app starts OIDC at `/api/oauth2/authorization/keycloak`, handles the fixed callback above, and initiates RP logout with CSRF-protected `POST /api/v1/auth/oidc-logout`. Local username/password authentication remains enabled. Logout is RP-initiated; no OIDC back-channel logout is configured. The backend keeps sessions in memory for a single replica. Role changes affect new authentication; an existing app session may retain its current authorities until re-login or its 30-minute session timeout. The local customer and admin fixture accounts have both been verified through the browser, including customer order ownership and admin-only order access.

## Production boundary

Use an existing managed Keycloak service and realm for deployed environments. Package the `ember-bean` theme in the Keycloak server image or mount it into the Keycloak pods, then set the realm's login theme to `ember-bean`. Configure the exact HTTPS public issuer, application URL, callback URI, confidential client, PKCE, self-registration policy, verified-email flow, password-recovery flow, SMTP delivery, and only the required client roles there. Enable order confirmations with `ORDER_CONFIRMATION_EMAIL_ENABLED=true` and configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_USERNAME`, and `SMTP_PASSWORD` using the provider's documented values. Keep SMTP credentials, client secrets, and operator/bootstrap credentials in deployment secret storage; never commit them, expose them in logs, or deploy this local `start-dev` service, Mailpit, or fixture users. Production requires verified email and TLS, and should use the identity provider's production deployment guidance rather than realm-import test data.

This change only provides local Compose infrastructure and documentation. Deployment Helm values and manifests in the separate GitOps repository are not changed.

## References

- [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers)
- [Keycloak 26.8.1 OIDC configuration attributes](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/protocol/oidc/OIDCConfigAttributes.java)
- [Keycloak 26.8.1 release](https://github.com/keycloak/keycloak/releases/tag/26.8.1)
- [Keycloak container guide](https://www.keycloak.org/server/containers)
- [Mailpit v1.31.6 release](https://github.com/axllent/mailpit/releases/tag/v1.31.6)
- [Keycloak realm import and export](https://www.keycloak.org/server/importExport)
- [Keycloak server administration guide](https://www.keycloak.org/docs/latest/server_admin/)
- [Spring Security OAuth 2.0 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
- [Spring Security OpenID Connect logout](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/logout.html)