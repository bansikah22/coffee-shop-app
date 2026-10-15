import { useState, type FormEvent } from 'react'
import type { Account } from './types'

type AuthPanelProps = {
  account: Account | null
  isLoading: boolean
  mode: 'login' | 'register'
  onLogin: (email: string, password: string) => Promise<unknown>
  onRegister: (email: string, password: string) => Promise<unknown>
}

export function AuthPanel({ account, isLoading, mode, onLogin, onRegister }: AuthPanelProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError('')
    try {
      if (mode === 'login') await onLogin(email, password)
      else await onRegister(email, password)
      setPassword('')
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Could not authenticate.')
    } finally {
      setSubmitting(false)
    }
  }

  if (isLoading) return <div className="auth-panel" aria-live="polite">Checking session...</div>
  if (account) return <p className="auth-error" role="status">You are already signed in as {account.email}.</p>

  return (
    <section className="auth-page" aria-labelledby="auth-title">
      <div className="auth-intro">
        <p className="eyebrow">{mode === 'login' ? 'Welcome back' : 'Join Ember & Bean'}</p>
        <h1 id="auth-title">{mode === 'login' ? <>Your next cup<br />is waiting.</> : <>Make room<br />at the table.</>}</h1>
        <p>{mode === 'login' ? 'Sign in to place an order and follow it from roast board to pickup.' : 'Create an account to order from today’s coffee board and keep your order history.'}</p>
      </div>
      <div className="auth-options">
        <div className="provider-option keycloak-option">
          <div><p className="eyebrow">Preferred sign-in</p><h2>Keycloak</h2><p>Single sign-on for organization-managed accounts.</p></div>
          <button className="secondary-button" type="button" disabled aria-describedby="keycloak-status">Continue with Keycloak</button>
          <small id="keycloak-status">SSO becomes available after the Keycloak realm and client are configured.</small>
        </div>
        <div className="local-option">
          <div className="provider-divider"><span>OR SIGN IN WITH EMAIL</span></div>
          <form className="auth-form" onSubmit={submit}>
            <label>Email<input type="email" autoComplete="email" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label>Password<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 12 : undefined} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
            {formError && <p className="auth-error" role="alert">{formError}</p>}
            <button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          </form>
        </div>
      </div>
    </section>
  )
}