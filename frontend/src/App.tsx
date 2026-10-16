import { useEffect, useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import './App.css'
import { CatalogManager } from './features/catalog/CatalogManager'
import { CoffeeBoard } from './features/catalog/CoffeeBoard'
import { useCoffeeCatalog } from './features/catalog/useCoffeeCatalog'
import { useCoffeeOrder } from './features/orders/useCoffeeOrder'
import { QuickOrderPanel } from './features/orders/QuickOrderPanel'
import { AuthPanel } from './features/auth/AuthPanel'
import { useAuth } from './features/auth/useAuth'
import { OrderHistory } from './features/orders/OrderHistory'
import { SiteNavigation } from './shared/navigation/SiteNavigation'
import { SiteFooter } from './shared/navigation/SiteFooter'

function App() {
  const [status, setStatus] = useState('Loading the coffee board...')
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const catalog = useCoffeeCatalog(setStatus, setError)
  const auth = useAuth(setError)
  const ordersPath = auth.account?.role === 'ADMIN' ? '/admin/orders' : auth.account ? '/orders' : null
  const order = useCoffeeOrder(catalog.coffees, ordersPath, setStatus, setError)
  const { loadCoffees } = catalog

  useEffect(() => {
    const loadTimer = window.setTimeout(() => { void loadCoffees() }, 0)
    return () => window.clearTimeout(loadTimer)
  }, [loadCoffees])

  return (
    <main>
      <SiteNavigation key={location.pathname} account={auth.account} isLoading={auth.isLoading} orderCount={order.orders.length} onLogout={auth.logout} />
      {error && <div className="notice error" role="alert">{error}<button type="button" onClick={() => setError('')} aria-label="Dismiss error">x</button></div>}
      {order.successMessage && <div className="order-success-toast" role="status" aria-live="polite">
        <div><strong>Order confirmed</strong><span>{order.successMessage}</span></div>
        <button type="button" onClick={order.dismissSuccessMessage} aria-label="Dismiss order confirmation">x</button>
      </div>}
      <p className="service-status" aria-live="polite"><span></span>{status}</p>
      <Routes>
        <Route path="/" element={
          <>
            <section className="intro" aria-labelledby="page-title">
              <p className="eyebrow">Coffee shop control room</p>
              <h1 id="page-title">Good coffee,<br />made deliberate.</h1>
              <p className="intro-copy">Browse today's roast board and order a carefully made cup.</p>
              <a className="browse-link" href="#coffee-board">Browse the board <span aria-hidden="true">down</span></a>
            </section>
            <CoffeeBoard
              coffees={catalog.coffees}
              onRefresh={() => void catalog.loadCoffees()}
              onOrder={(coffeeId) => {
                if (!auth.account) navigate('/login')
                else order.setSelectedCoffeeId(coffeeId)
              }}
            />
            {auth.account && <QuickOrderPanel
              coffee={order.selectedCoffee}
              quantity={order.quantity}
              fulfillmentType={order.fulfillmentType}
              customerPhone={order.customerPhone}
              deliveryAddress={order.deliveryAddress}
              isSubmitting={order.isSubmitting}
              onQuantityChange={order.setQuantity}
              onFulfillmentTypeChange={order.setFulfillmentType}
              onCustomerPhoneChange={order.setCustomerPhone}
              onDeliveryAddressChange={order.setDeliveryAddress}
              onPlaceOrder={() => void order.placeOrder()}
              onCancel={() => order.setSelectedCoffeeId(null)}
            />}
          </>
        } />
        <Route path="/login" element={
          auth.isLoading ? <p className="route-loading">Checking your account...</p>
            : <AuthPanel account={auth.account} isLoading={auth.isLoading} mode="login" onLogin={async (email, password) => {
                  const signedInAccount = await auth.login(email, password)
                  navigate(signedInAccount.role === 'ADMIN' ? '/admin' : '/')
                }} onRegister={auth.register} />
        } />
        <Route path="/register" element={
          auth.isLoading ? <p className="route-loading">Checking your account...</p>
            : <AuthPanel account={auth.account} isLoading={auth.isLoading} mode="register" onLogin={auth.login} onRegister={async (...credentials) => { await auth.register(...credentials); navigate('/') }} />
        } />
        <Route path="/orders" element={
          auth.isLoading ? <p className="route-loading">Checking your account...</p>
            : !auth.account ? <Navigate to="/login" replace />
              : <OrderHistory orders={order.orders} isAdmin={false} onRefresh={() => void order.loadOrders()} />
        } />
        <Route path="/admin" element={
          auth.isLoading ? <p className="route-loading">Checking your account...</p>
            : !auth.account ? <Navigate to="/login" replace />
              : auth.account.role !== 'ADMIN' ? <section className="access-denied"><p className="eyebrow">403 · Restricted</p><h1>Admin access only.</h1><p>Your account can browse coffee and view your own orders.</p><Link className="browse-link" to="/">Back to coffee</Link></section>
                : <>
                    <section className="route-heading"><p className="eyebrow">Administrator workspace</p><h1>Store overview.</h1></section>
                    <OrderHistory orders={order.orders} isAdmin onRefresh={() => void order.loadOrders()}
                      onMarkReady={(orderId) => void order.markOrderReady(orderId)}
                      onMarkCompleted={(orderId) => void order.markOrderCompleted(orderId)}
                      markingOrderReady={order.markingOrderReady} />
                    <CatalogManager
                      coffees={catalog.coffees}
                      form={catalog.form}
                      editingId={catalog.editingId}
                      isSubmitting={catalog.isSubmitting}
                      onFormChange={catalog.setForm}
                      onSubmit={catalog.saveCoffee}
                      onCancelEdit={catalog.resetForm}
                      onEdit={catalog.startEdit}
                      onDelete={(coffee) => void catalog.deleteCoffee(coffee)}
                    />
                  </>
        } />
        <Route path="*" element={<section className="access-denied"><p className="eyebrow">404 · Page not found</p><h1>This page isn't on the menu.</h1><Link className="browse-link" to="/">Back to coffee</Link></section>} />
      </Routes>
      <SiteFooter account={auth.account} />
    </main>
  )
}

export default App
