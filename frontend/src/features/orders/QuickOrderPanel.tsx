import type { Coffee } from '../catalog/types'

type QuickOrderPanelProps = {
  coffee: Coffee | undefined
  quantity: number
  isSubmitting: boolean
  onQuantityChange: (quantity: number) => void
  onPlaceOrder: () => void
  onCancel: () => void
}

export function QuickOrderPanel({ coffee, quantity, isSubmitting, onQuantityChange, onPlaceOrder, onCancel }: QuickOrderPanelProps) {
  return (
    <section className="order-panel" aria-labelledby="order-title">
      <div><p className="eyebrow">Quick order</p><h2 id="order-title">Make it happen.</h2></div>
      {coffee ? (
        <div className="order-controls">
          <p><strong>{coffee.name}</strong><span>${coffee.price.toFixed(2)} each</span></p>
          <label>Quantity<input type="number" min="1" value={quantity} onChange={(event) => onQuantityChange(Math.max(1, Number(event.target.value)))} /></label>
          <button type="button" className="primary-button" disabled={isSubmitting} onClick={onPlaceOrder}>{isSubmitting ? 'Placing...' : 'Place order'}</button>
          <button type="button" className="icon-button" onClick={onCancel} aria-label="Cancel order">x</button>
        </div>
      ) : <p className="order-placeholder">Choose an available coffee from the board to begin.</p>}
    </section>
  )
}