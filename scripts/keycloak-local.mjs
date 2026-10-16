import { randomBytes } from 'node:crypto'
import { chmod, readFile, writeFile } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const projectRoot = fileURLToPath(new URL('../', import.meta.url))
const envPath = path.join(projectRoot, '.env')
const envTemplatePath = path.join(projectRoot, '.env.example')
const action = process.argv[2] ?? 'up'
const secretKeys = ['KEYCLOAK_CLIENT_SECRET', 'KEYCLOAK_ADMIN_PASSWORD', 'KEYCLOAK_DB_PASSWORD']

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
    env: { ...process.env, KEYCLOAK_CLIENT_SECRET: values.get('KEYCLOAK_CLIENT_SECRET') },
  })
  if (renderer.status !== 0) throw new Error('Could not render the local Keycloak realm.')
  console.log('Local Keycloak credentials are stored in the ignored .env file (mode 600).')
  return values
}

if (action === 'prepare') {
  await prepare()
} else if (['up', 'down', 'status'].includes(action)) {
  const values = action === 'down' ? null : await prepare()
  const composeArgs = ['compose', '--profile', 'sso']
  if (action === 'up') composeArgs.push('up', '--build', '--pull', 'missing', '--detach', '--wait')
  if (action === 'down') composeArgs.push('down')
  if (action === 'status') composeArgs.push('ps')
  const composeEnv = action === 'up' ? {
    ...process.env,
    KEYCLOAK_ENABLED: 'true',
    ORDER_CONFIRMATION_EMAIL_ENABLED: 'true',
    SMTP_HOST: 'mailpit',
    SMTP_PORT: '1025',
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