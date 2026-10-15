import { Link } from 'react-router-dom'
import type { Account } from '../../features/auth/types'

type SiteFooterProps = {
  account: Account | null
}

export function SiteFooter({ account }: SiteFooterProps) {
  return (
    <footer className="site-footer">
      <div className="footer-main">
        <div className="footer-brand">
          <Link className="brand-link" to="/">
            <img className="brand-mark" src="/ember-bean-mark.svg" alt="" aria-hidden="true" />
            <span className="wordmark">EMBER <span>&amp;</span> BEAN</span>
          </Link>
          <p>Thoughtfully roasted. Carefully poured. Made for the moments in between.</p>
        </div>
        <nav className="footer-links" aria-label="Footer navigation">
          <Link to="/">Coffee board</Link>
          {account && <Link to="/orders">Your orders</Link>}
          {account?.role === 'ADMIN' && <Link to="/admin">Admin workspace</Link>}
          {!account && <Link to="/login">Sign in</Link>}
        </nav>
        <p className="footer-note">Good things take the time they take.</p>
      </div>
      <div className="footer-bottom">
        <span>© {new Date().getFullYear()} Ember &amp; Bean</span>
        <span>Roasted with intention.</span>
      </div>
    </footer>
  )
}