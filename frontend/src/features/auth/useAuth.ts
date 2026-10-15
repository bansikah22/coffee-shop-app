import { useCallback, useEffect, useState } from 'react'
import { request } from '../../shared/api/request'
import type { Account } from './types'

export function useAuth(setError: (message: string) => void) {
  const [account, setAccount] = useState<Account | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const loadAccount = useCallback(async () => {
    try {
      await request('/auth/csrf')
      setAccount(await request<Account>('/auth/me'))
    } catch {
      setAccount(null)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => { void loadAccount() }, 0)
    return () => window.clearTimeout(timer)
  }, [loadAccount])

  async function login(email: string, password: string): Promise<Account> {
    setError('')
    const authenticatedAccount = await request<Account>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setAccount(authenticatedAccount)
    return authenticatedAccount
  }

  async function register(email: string, password: string): Promise<Account> {
    setError('')
    await request('/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) })
    return login(email, password)
  }

  async function logout() {
    await request<void>('/auth/logout', { method: 'POST' })
    setAccount(null)
  }

  return { account, isLoading, login, register, logout, refresh: loadAccount }
}