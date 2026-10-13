import { type SyntheticEvent, useEffect, useState } from 'react'
import './App.css'

type Coffee = { id: number; name: string; description: string; price: number; available: boolean }
type CoffeeForm = Omit<Coffee, 'id'>

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const emptyCoffee: CoffeeForm = { name: '', description: '', price: 0, available: true }

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.detail ?? body?.message ?? 'The request could not be completed.')
  }

  return response.status === 204 ? (undefined as T) : response.json() as Promise<T>
}

function App() {
  const [coffees, setCoffees] = useState<Coffee[]>([])
  const [form, setForm] = useState<CoffeeForm>(emptyCoffee)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [selectedCoffeeId, setSelectedCoffeeId] = useState<number | null>(null)
  const [status, setStatus] = useState('Loading the coffee board...')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function loadCoffees() {
    try {
      setError('')
      const catalog = await request<Coffee[]>('/coffees')
      setCoffees(catalog)
      setStatus(`${catalog.filter((coffee) => coffee.available).length} coffees ready to order`)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not load the coffee board.')
      setStatus('Catalog unavailable')
    }
  }

  useEffect(() => {
    const loadTimer = window.setTimeout(() => { void loadCoffees() }, 0)
    return () => window.clearTimeout(loadTimer)
  }, [])

  function resetForm() { setForm(emptyCoffee); setEditingId(null) }

  function startEdit(coffee: Coffee) {
    setEditingId(coffee.id)
    setForm({ name: coffee.name, description: coffee.description, price: coffee.price, available: coffee.available })
    document.getElementById('catalog-manager')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function saveCoffee(event: SyntheticEvent<HTMLFormElement, SubmitEvent>) {
    event.preventDefault()
    setIsSubmitting(true)
    setError('')
    try {
      const saved = await request<Coffee>(editingId === null ? '/coffees' : `/coffees/${editingId}`, {
        method: editingId === null ? 'POST' : 'PUT', body: JSON.stringify(form),
      })
      setStatus(`${saved.name} ${editingId === null ? 'added to' : 'updated in'} the catalog`)
      resetForm()
      await loadCoffees()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not save this coffee.')
    } finally { setIsSubmitting(false) }
  }

  async function deleteCoffee(coffee: Coffee) {
    if (!window.confirm(`Remove ${coffee.name} from the catalog?`)) return
    setError('')
    try {
      await request<void>(`/coffees/${coffee.id}`, { method: 'DELETE' })
      if (editingId === coffee.id) resetForm()
      setStatus(`${coffee.name} removed from the catalog`)
      await loadCoffees()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not remove this coffee.')
    }
  }

  async function placeOrder() {
    if (selectedCoffeeId === null) return
    setIsSubmitting(true)
    setError('')
    try {
      const order = await request<{ coffeeName: string; quantity: number; totalPrice: number }>('/orders', {
        method: 'POST', body: JSON.stringify({ coffeeId: selectedCoffeeId, quantity }),
      })
      setStatus(`Order placed: ${order.quantity} ${order.coffeeName} for $${order.totalPrice.toFixed(2)}`)
      setSelectedCoffeeId(null)
      setQuantity(1)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not place this order.')
    } finally { setIsSubmitting(false) }
  }

  const selectedCoffee = coffees.find((coffee) => coffee.id === selectedCoffeeId)

  return (
    <main>
      <header className="site-header"><a className="wordmark" href="#coffee-board">EMBER &amp; BEAN</a><p className="service-status" aria-live="polite"><span></span>{status}</p></header>
      <section className="intro" aria-labelledby="page-title"><p className="eyebrow">Coffee shop control room</p><h1 id="page-title">Good coffee,<br />made deliberate.</h1><p className="intro-copy">Browse today's roast board, place an order, or keep the menu current from one focused workspace.</p><a className="browse-link" href="#coffee-board">Browse the board <span aria-hidden="true">down</span></a></section>
      {error && <div className="notice error" role="alert">{error}<button type="button" onClick={() => setError('')} aria-label="Dismiss error">x</button></div>}
      <section className="coffee-board" id="coffee-board" aria-labelledby="board-title">
        <div className="section-heading"><div><p className="eyebrow">Today's selection</p><h2 id="board-title">The coffee board</h2></div><button className="refresh-button" type="button" onClick={() => void loadCoffees()}>Refresh</button></div>
        <div className="coffee-grid">{coffees.map((coffee, index) => <article className={`coffee-card card-tone-${index % 4}`} key={coffee.id}><div className="coffee-card-top"><span className="coffee-number">0{index + 1}</span><span className={coffee.available ? 'availability available' : 'availability'}>{coffee.available ? 'Available' : 'Paused'}</span></div><div className="coffee-copy"><h3>{coffee.name}</h3><p>{coffee.description}</p></div><div className="coffee-card-bottom"><strong>${coffee.price.toFixed(2)}</strong><button type="button" disabled={!coffee.available} onClick={() => setSelectedCoffeeId(coffee.id)}>Order</button></div></article>)}</div>
      </section>
      <section className="order-panel" aria-labelledby="order-title"><div><p className="eyebrow">Quick order</p><h2 id="order-title">Make it happen.</h2></div>{selectedCoffee ? <div className="order-controls"><p><strong>{selectedCoffee.name}</strong><span>${selectedCoffee.price.toFixed(2)} each</span></p><label>Quantity<input type="number" min="1" value={quantity} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value)))} /></label><button type="button" className="primary-button" disabled={isSubmitting} onClick={() => void placeOrder()}>{isSubmitting ? 'Placing...' : 'Place order'}</button><button type="button" className="icon-button" onClick={() => setSelectedCoffeeId(null)} aria-label="Cancel order">x</button></div> : <p className="order-placeholder">Choose an available coffee from the board to begin.</p>}</section>
      <section className="manager" id="catalog-manager" aria-labelledby="manager-title"><div className="manager-intro"><p className="eyebrow">Catalog management</p><h2 id="manager-title">Keep the board sharp.</h2><p>Add a new roast, change availability, or remove a finished offering.</p></div><form className="coffee-form" onSubmit={saveCoffee}><label>Name<input required maxLength={100} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label>Price<input required min="0" step="0.01" type="number" value={form.price} onChange={(event) => setForm({ ...form, price: Number(event.target.value) })} /></label><label className="description-field">Description<textarea required maxLength={500} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><label className="availability-toggle"><input type="checkbox" checked={form.available} onChange={(event) => setForm({ ...form, available: event.target.checked })} />Available for ordering</label><div className="form-actions"><button className="primary-button" disabled={isSubmitting} type="submit">{editingId === null ? 'Add coffee' : 'Save changes'}</button>{editingId !== null && <button className="secondary-button" type="button" onClick={resetForm}>Cancel edit</button>}</div></form><div className="management-list" aria-label="Catalog entries">{coffees.map((coffee) => <div className="management-row" key={coffee.id}><div><strong>{coffee.name}</strong><span>{coffee.available ? 'On board' : 'Paused'} · ${coffee.price.toFixed(2)}</span></div><div className="row-actions"><button type="button" onClick={() => startEdit(coffee)}>Edit</button><button type="button" className="danger-button" onClick={() => void deleteCoffee(coffee)}>Remove</button></div></div>)}</div></section>
    </main>
  )
}

export default App
