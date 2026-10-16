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

const check = await fetch(endpoint, { headers })
if (!check.ok) throw new Error(`Could not verify local realm settings (${check.status}).`)
const realm = await check.json()
  if (realm.loginTheme !== 'ember-bean' || realm.displayName !== 'Ember & Bean'
    || !realm.registrationAllowed || !realm.registrationEmailAsUsername || !realm.verifyEmail || !realm.resetPasswordAllowed
    || realm.smtpServer?.host !== 'mailpit' || realm.smtpServer?.port !== '1025') {
  throw new Error('Keycloak did not retain the expected self-registration settings.')
}
console.log('Keycloak verified self-registration, email-as-username, and password reset are enabled.')