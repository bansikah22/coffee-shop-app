import { randomBytes, createHmac } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync, chmodSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseEnv } from 'node:util'
import { spawnSync } from 'node:child_process'
import { isSandboxSecretKey } from './sandbox-key.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repository = resolve(root, '..')
const envFile = resolve(root, '.env')
const command = process.argv[2] ?? 'help'

function execute(program, args, environment = {}) {
  const child = spawnSync(program, args, { cwd: root, stdio: 'inherit', env: { ...process.env, ...environment } })
  if (child.error) throw new Error(`Could not execute ${program}`)
  if (child.status !== 0) process.exit(child.status ?? 1)
}

function configuration() {
  if (!existsSync(envFile)) throw new Error('Run node scripts/local.mjs init first')
  return parseEnv(readFileSync(envFile, 'utf8'))
}

function compose(args) {
  const rootEnv = resolve(repository, '.env')
  const environmentFiles = existsSync(rootEnv) ? ['--env-file', rootEnv, '--env-file', envFile] : ['--env-file', envFile]
  execute('docker', ['compose', ...environmentFiles, '-f', resolve(repository, 'docker-compose.yml'), ...args])
}

function token(config) {
  if (!config.PAYMENT_JWT_SECRET) throw new Error('Missing local JWT secret')
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
  const now = Math.floor(Date.now() / 1000)
  const unsigned = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    iss: 'payment-poc', aud: 'payment-service', azp: 'payment-service', sub: 'local-demo',
    iat: now, exp: now + 300,
  })}`
  return `${unsigned}.${createHmac('sha256', config.PAYMENT_JWT_SECRET).update(unsigned).digest('base64url')}`
}

try {
  if (command === 'init') {
    const config = existsSync(envFile) ? configuration() : {}
    for (const key of ['PAYMENT_DB_PASSWORD', 'DEMO_DB_PASSWORD', 'RABBITMQ_PASSWORD', 'PAYMENT_JWT_SECRET']) {
      config[key] ||= randomBytes(32).toString('hex')
    }
    config.RABBITMQ_USER ||= 'payment_demo'
    config.STRIPE_SECRET_KEY ||= ''
    config.STRIPE_WEBHOOK_SECRET ||= ''
    writeFileSync(envFile, Object.entries(config).map(([key, value]) => `${key}=${JSON.stringify(value)}`).join('\n') + '\n', { mode: 0o600 })
    chmodSync(envFile, 0o600)
    console.log('Private .env initialized; existing values preserved. Add sandbox Stripe secrets directly in that file.')
  } else if (command === 'infra') {
    configuration()
    compose(['--profile', 'payments-infra', 'up', '-d', '--wait', 'payments-db', 'demo-db', 'rabbitmq'])
  } else if (command === 'up') {
    const config = configuration()
    if (!isSandboxSecretKey(config.STRIPE_SECRET_KEY) || !config.STRIPE_WEBHOOK_SECRET?.startsWith('whsec_')) {
      throw new Error('Set sandbox STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET directly in .env before starting payments')
    }
    execute('mvn', ['--batch-mode', '--no-transfer-progress', 'verify'])
    compose(['--profile', 'payments', 'up', '-d', '--build', 'payments', 'demo-consumer'])
  } else if (command === 'down') {
    compose(['--profile', 'payments', 'stop', 'payments', 'demo-consumer', 'payments-db', 'demo-db', 'rabbitmq'])
  } else if (command === 'test-infra') {
    const config = configuration()
    compose(['--profile', 'payments-infra', 'up', '-d', '--wait', 'payments-db', 'demo-db', 'rabbitmq'])
    execute('mvn', ['--batch-mode', '--no-transfer-progress', 'verify'], {
      PAYMENT_INFRA_TEST: 'true', PAYMENT_IT_DB_PASSWORD: config.PAYMENT_DB_PASSWORD,
      PAYMENT_IT_DEMO_PASSWORD: config.DEMO_DB_PASSWORD,
      PAYMENT_IT_RABBIT_USER: config.RABBITMQ_USER, PAYMENT_IT_RABBIT_PASSWORD: config.RABBITMQ_PASSWORD,
    })
  } else if (command === 'checkout' || command === 'status') {
    const config = configuration()
    const order = process.argv[3]
    if (!order) throw new Error('Provide an order reference for checkout, or a payment ID for status')
    const options = { headers: { Authorization: `Bearer ${token(config)}` } }
    let path = `/internal/payments/${encodeURIComponent(order)}`
    if (command === 'checkout') {
      options.method = 'POST'
      options.headers['Content-Type'] = 'application/json'
      options.headers['Idempotency-Key'] = `checkout-${order}`
      options.body = JSON.stringify({ orderReference: order, amountMinor: Number(process.argv[4] ?? 300), currency: 'USD' })
      path = '/internal/payments'
    }
    const response = await fetch(`http://localhost:18081${path}`, options)
    console.log(`HTTP ${response.status}\n${await response.text()}`)
    if (!response.ok) process.exitCode = 1
  } else {
    console.log('Commands: init | infra | test-infra | up | down | checkout ORDER [USD_CENTS] | status PAYMENT_ID')
  }
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}