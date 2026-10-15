const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const method = options?.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(options?.headers)

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfResponse = await fetch(`${apiBaseUrl}/auth/csrf`, { credentials: 'same-origin' })
    if (!csrfResponse.ok) throw new Error('Could not establish a secure session.')
    const csrf = await csrfResponse.json() as { headerName: string; token: string }
    headers.set(csrf.headerName, csrf.token)
  }

  if (options?.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    credentials: 'same-origin',
    headers,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.detail ?? body?.message ?? 'The request could not be completed.')
  }

  return response.status === 204 ? (undefined as T) : response.json() as Promise<T>
}