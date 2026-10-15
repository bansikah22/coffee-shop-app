import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { ShoppingBag } from 'lucide-react'
import type { Account } from '../../features/auth/types'

type SiteNavigationProps = {
  account: Account | null
  isLoading: boolean
  orderCount: number
  onLogout: () => Promise<void>
}

export function SiteNavigation({ account, isLoading, orderCount, onLogout }: SiteNavigationProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()

  async function signOut() {
    await onLogout()
    setMenuOpen(false)
    navigate('/')
  }

  function closeMenu() {
    setMenuOpen(false)
  }

  return (
    <header className="site-header">
      <Link className="brand-link" to="/" onClick={closeMenu}>
        <img className="brand-mark" src="/ember-bean-mark.svg" alt="" aria-hidden="true" />
        <span className="wordmark">EMBER <span>&amp;</span> BEAN</span>
      </Link>
      <button
        className="nav-toggle"
        type="button"
        aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}
        aria-expanded={menuOpen}
        aria-controls="primary-navigation"
        onClick={() => setMenuOpen((open) => !open)}
      >
        <span></span><span></span>
      </button>
      <nav id="primary-navigation" className={menuOpen ? 'primary-navigation is-open' : 'primary-navigation'} aria-label="Main navigation">
        <NavLink to="/" end onClick={closeMenu}>Coffee</NavLink>
        {account && <NavLink className="nav-orders" to="/orders" onClick={closeMenu} aria-label={`Orders${orderCount ? `, ${orderCount} total` : ''}`}>
          <ShoppingBag size={18} strokeWidth={1.8} aria-hidden="true" />
          <span>Orders</span>
          {orderCount > 0 && <span className="order-count" aria-hidden="true">{orderCount > 99 ? '99+' : orderCount}</span>}
        </NavLink>}
        {account?.role === 'ADMIN' && <NavLink to="/admin" onClick={closeMenu}>Admin</NavLink>}
        {!isLoading && !account && <NavLink className="nav-account" to="/login" onClick={closeMenu}>Sign in</NavLink>}
        {!isLoading && !account && <Link className="nav-register" to="/register" onClick={closeMenu}>Create account</Link>}
        {account && <div className="nav-user"><span>{account.email}</span><button type="button" onClick={() => void signOut()}>Sign out</button></div>}
      </nav>
    </header>
  )
}