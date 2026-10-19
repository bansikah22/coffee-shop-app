import { randomBytes } from 'node:crypto'
import { chmod, readFile, writeFile } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const projectRoot = fileURLToPath(new URL('../', import.meta.url))
const envPath = path.join(projectRoot, '.env')
const paymentEnvPath = path.join(projectRoot, 'payment-service', '.env')
const envTemplatePath = path.join(projectRoot, '.env.example')
const action = process.argv[2] ?? 'up'
const secretKeys = ['KEYCLOAK_CLIENT_SECRET', 'KEYCLOAK_ADMIN_PASSWORD', 'KEYCLOAK_DB_PASSWORD', 'PAYMENT_CLIENT_SECRET']

function parseEnv(contents) {
  return new Map(contents.split(/\r?\n/).filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const separator = line.indexOf('=')
      if (separator < 1) throw new Error(`Invalid .env entry: ${line}`)
      return [line.slice(0, separator), line.slice(separator + 1)]
    }))
}

async function prepare() {
  let contents
  try {
    contents = await readFile(envPath, 'utf8')
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
    contents = await readFile(envTemplatePath, 'utf8')
  }

  const values = parseEnv(contents)
  for (const key of secretKeys) {
    if (!values.get(key) || key === 'KEYCLOAK_DB_PASSWORD' && values.get(key) === 'keycloak_local') {
      values.set(key, randomBytes(32).toString('base64url'))
    }
  }
  const paymentInit = spawnSync(process.execPath, [path.join(projectRoot, 'payment-service/scripts/local.mjs'), 'init'], {
    cwd: projectRoot,
    stdio: 'inherit',
  })
  if (paymentInit.error || paymentInit.status !== 0) throw new Error('Could not initialize private payment settings.')
  values.set('KEYCLOAK_ENABLED', 'false')
  const updated = contents.split(/\r?\n/).map((line) => {
    const separator = line.indexOf('=')
    if (separator < 1) return line
    const key = line.slice(0, separator)
    return values.has(key) ? `${key}=${values.get(key)}` : line
  })
  for (const [key, value] of values) {
    if (!contents.split(/\r?\n/).some((line) => line.startsWith(`${key}=`))) updated.push(`${key}=${value}`)
  }
  await writeFile(envPath, `${updated.join('\n').replace(/\n+$/, '')}\n`, { mode: 0o600 })
  await chmod(envPath, 0o600)

  const renderer = spawnSync(process.execPath, [path.join(projectRoot, 'scripts/render-keycloak-realm.mjs')], {
    cwd: projectRoot,
    stdio: 'inherit',
    env: { ...process.env, ...Object.fromEntries(values) },
  })
  if (renderer.status !== 0) throw new Error('Could not render the local Keycloak realm.')
  console.log('Local Keycloak credentials are stored in the ignored .env file (mode 600).')
  return values
}

if (action === 'prepare') {
  await prepare()
} else if (['up', 'down', 'status'].includes(action)) {
  const values = action === 'down' ? null : await prepare()
  if (action === 'up') {
    for (const [label, arguments_] of [
      ['coffee backend', ['-f', path.join(projectRoot, 'backend/pom.xml'), '--batch-mode', '--no-transfer-progress', 'package', '-DskipTests']],
      ['payment service', ['-f', path.join(projectRoot, 'payment-service/pom.xml'), '--batch-mode', '--no-transfer-progress', 'verify']],
    ]) {
      console.log(`Packaging ${label} before building its runtime image.`)
      const packageResult = spawnSync('mvn', arguments_, { cwd: projectRoot, stdio: 'inherit' })
      if (packageResult.error) throw packageResult.error
      if (packageResult.status !== 0) process.exit(packageResult.status ?? 1)
    }
  }
  const composeArgs = ['compose', '--env-file', envPath, '--env-file', paymentEnvPath, '--profile', 'sso', '--profile', 'payments']
  if (action === 'up') composeArgs.push('up', '--build', '--pull', 'missing', '--detach', '--wait')
  if (action === 'down') composeArgs.push('down')
  if (action === 'status') composeArgs.push('ps')
  const composeEnv = action === 'up' ? {
    ...process.env,
    KEYCLOAK_ENABLED: 'true',
    PAYMENTS_ENABLED: 'true',
    PAYMENT_ISSUER_URI: `http://localhost:${values.get('KEYCLOAK_PORT') || '8180'}/realms/coffee-shop`,
    PAYMENT_JWK_SET_URI: 'http://keycloak:8080/realms/coffee-shop/protocol/openid-connect/certs',
    ORDER_CONFIRMATION_EMAIL_ENABLED: 'true',
    SMTP_HOST: 'mailpit',
    SMTP_PORT: '1025',
    SMTP_AUTH: 'false',
    SMTP_STARTTLS: 'false',
  } : process.env
  const compose = spawnSync('docker', composeArgs, { cwd: projectRoot, stdio: 'inherit', env: composeEnv })
  if (compose.error) throw compose.error
  if (compose.status !== 0) process.exitCode = compose.status ?? 1
  if (action === 'up' && compose.status === 0) {
    const configure = spawnSync(process.execPath, [path.join(projectRoot, 'scripts/configure-keycloak-realm.mjs')], {
      cwd: projectRoot,
      stdio: 'inherit',
      env: { ...process.env, ...Object.fromEntries(values) },
    })
    if (configure.error) throw configure.error
    if (configure.status !== 0) process.exitCode = configure.status ?? 1
  }
} else {
  console.error('Usage: node scripts/keycloak-local.mjs [prepare|up|down|status]')
  process.exitCode = 2
}