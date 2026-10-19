import { mkdir, readFile, writeFile } from 'node:fs/promises'

const secret = process.env.KEYCLOAK_CLIENT_SECRET
const paymentSecret = process.env.PAYMENT_CLIENT_SECRET
if (!secret || secret.length < 32 || !/^[A-Za-z0-9_-]+$/.test(secret)
    || !paymentSecret || paymentSecret.length < 32 || !/^[A-Za-z0-9_-]+$/.test(paymentSecret)) {
  throw new Error('Set both local Keycloak client secrets to generated values of at least 32 URL-safe characters.')
}

const templatePath = new URL('../keycloak/coffee-shop-realm.json', import.meta.url)
const outputPath = new URL('../.local/keycloak/coffee-shop-realm.json', import.meta.url)
const realm = JSON.parse(await readFile(templatePath, 'utf8'))
const client = realm.clients.find(({ clientId }) => clientId === 'coffee-shop')
const paymentClient = realm.clients.find(({ clientId }) => clientId === 'payment-service')
if (!client || client.secret !== '${KEYCLOAK_CLIENT_SECRET}' || !paymentClient
    || paymentClient.secret !== '${PAYMENT_CLIENT_SECRET}') {
  throw new Error('The realm template has no expected confidential client secret placeholder.')
}

client.secret = secret
paymentClient.secret = paymentSecret
await mkdir(new URL('../.local/keycloak/', import.meta.url), { recursive: true, mode: 0o700 })
await writeFile(outputPath, `${JSON.stringify(realm, null, 2)}\n`, { mode: 0o600 })
console.log('Rendered local Keycloak realm with generated client credentials.')