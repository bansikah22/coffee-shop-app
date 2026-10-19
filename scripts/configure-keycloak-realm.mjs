const baseUrl = `http://localhost:${process.env.KEYCLOAK_PORT || '8180'}`
const form = new URLSearchParams({
  grant_type: 'password',
  client_id: 'admin-cli',
  username: process.env.KEYCLOAK_ADMIN_USERNAME || 'admin',
  password: process.env.KEYCLOAK_ADMIN_PASSWORD || '',
})

if (!form.get('password')) throw new Error('KEYCLOAK_ADMIN_PASSWORD is required to configure the local realm.')

const tokenResponse = await fetch(`${baseUrl}/realms/master/protocol/openid-connect/token`, {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: form,
})
if (!tokenResponse.ok) throw new Error(`Could not authenticate to the local Keycloak admin API (${tokenResponse.status}).`)
const { access_token: accessToken } = await tokenResponse.json()
const endpoint = `${baseUrl}/admin/realms/coffee-shop`
const headers = { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' }
const update = await fetch(endpoint, {
  method: 'PUT',
  headers,
  body: JSON.stringify({
    displayName: 'Ember & Bean',
    loginTheme: 'ember-bean',
    registrationAllowed: true,
    registrationEmailAsUsername: true,
    verifyEmail: true,
    resetPasswordAllowed: true,
    smtpServer: {
      host: 'mailpit',
      port: '1025',
      from: 'no-reply@ember-bean.local',
      fromDisplayName: 'Ember & Bean',
      ssl: 'false',
      starttls: 'false',
      auth: 'false',
    },
  }),
})
if (!update.ok) throw new Error(`Could not enable verified self-registration (${update.status}).`)

if (!process.env.PAYMENT_CLIENT_SECRET) throw new Error('PAYMENT_CLIENT_SECRET is required for the order-service workload client.')
const paymentScopeResponse = await fetch(`${endpoint}/client-scopes?search=payments-workload`, { headers })
if (!paymentScopeResponse.ok) throw new Error(`Could not inspect the payment client scope (${paymentScopeResponse.status}).`)
const paymentScopes = await paymentScopeResponse.json()
const existingScope = paymentScopes.find((scope) => scope.name === 'payments-workload')
const paymentScope = {
  name: 'payments-workload',
  description: 'Service identity scope and audience for coffee orders calling payments',
  protocol: 'openid-connect',
  attributes: { 'include.in.token.scope': 'false', 'display.on.consent.screen': 'false' },
  protocolMappers: [
    { name: 'Payments audience', protocol: 'openid-connect', protocolMapper: 'oidc-audience-mapper',
      consentRequired: false, config: { 'included.client.audience': 'payment-service', 'id.token.claim': 'false', 'access.token.claim': 'true' } },
  ],
}
const scopeResponse = await fetch(existingScope ? `${endpoint}/client-scopes/${existingScope.id}` : `${endpoint}/client-scopes`, {
  method: existingScope ? 'PUT' : 'POST', headers, body: JSON.stringify(paymentScope),
})
if (!scopeResponse.ok) throw new Error(`Could not configure the payment client scope (${scopeResponse.status}).`)

const clientsResponse = await fetch(`${endpoint}/clients?clientId=payment-service`, { headers })
if (!clientsResponse.ok) throw new Error(`Could not inspect the payment service client (${clientsResponse.status}).`)
const clients = await clientsResponse.json()
const existingClient = clients[0]
const paymentClient = {
  ...(existingClient ?? {}),
  clientId: 'payment-service',
  name: 'Coffee Shop order service to payment service',
  enabled: true,
  protocol: 'openid-connect',
  clientAuthenticatorType: 'client-secret',
  secret: process.env.PAYMENT_CLIENT_SECRET,
  publicClient: false,
  standardFlowEnabled: false,
  implicitFlowEnabled: false,
  directAccessGrantsEnabled: false,
  serviceAccountsEnabled: true,
  fullScopeAllowed: false,
  defaultClientScopes: [...new Set([...(existingClient?.defaultClientScopes ?? []), 'payments-workload'])],
  redirectUris: [],
  webOrigins: [],
}
const clientResponse = await fetch(existingClient ? `${endpoint}/clients/${existingClient.id}` : `${endpoint}/clients`, {
  method: existingClient ? 'PUT' : 'POST', headers, body: JSON.stringify(paymentClient),
})
if (!clientResponse.ok) throw new Error(`Could not configure the payment service client (${clientResponse.status}).`)

const check = await fetch(endpoint, { headers })
if (!check.ok) throw new Error(`Could not verify local realm settings (${check.status}).`)
const realm = await check.json()
  if (realm.loginTheme !== 'ember-bean' || realm.displayName !== 'Ember & Bean'
    || !realm.registrationAllowed || !realm.registrationEmailAsUsername || !realm.verifyEmail || !realm.resetPasswordAllowed
    || realm.smtpServer?.host !== 'mailpit' || realm.smtpServer?.port !== '1025') {
  throw new Error('Keycloak did not retain the expected self-registration settings.')
}
console.log('Keycloak verified self-registration, password reset, and the payment service client are configured.')