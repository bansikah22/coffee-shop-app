import type { FormEvent } from 'react'
import type { Coffee } from '../catalog/types'
import type { FulfillmentType } from './useCoffeeOrder'

type QuickOrderPanelProps = {
  coffee: Coffee | undefined
  quantity: number
  fulfillmentType: FulfillmentType
  customerPhone: string
  deliveryAddress: string
  isSubmitting: boolean
  onQuantityChange: (quantity: number) => void
  onFulfillmentTypeChange: (fulfillmentType: FulfillmentType) => void
  onCustomerPhoneChange: (phone: string) => void
  onDeliveryAddressChange: (address: string) => void
  onPlaceOrder: () => void
  onCancel: () => void
}

export function QuickOrderPanel({ coffee, quantity, fulfillmentType, customerPhone, deliveryAddress, isSubmitting,
  onQuantityChange, onFulfillmentTypeChange, onCustomerPhoneChange, onDeliveryAddressChange, onPlaceOrder, onCancel }: QuickOrderPanelProps) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onPlaceOrder()
  }

  return (
    <section className="order-panel" aria-labelledby="order-title">
      <div><p className="eyebrow">Quick order</p><h2 id="order-title">Make it happen.</h2></div>
      {coffee ? (
        <form className="order-checkout" onSubmit={submit}>
          <p className="order-product"><strong>{coffee.name}</strong><span>${coffee.price.toFixed(2)} each</span></p>
          <fieldset className="fulfillment-choice">
            <legend>How would you like to receive your order?</legend>
            <div className="fulfillment-switch" role="group" aria-label="Fulfillment method">
              <button type="button" aria-pressed={fulfillmentType === 'PICKUP'} onClick={() => onFulfillmentTypeChange('PICKUP')}>Pickup</button>
              <button type="button" aria-pressed={fulfillmentType === 'DELIVERY'} onClick={() => onFulfillmentTypeChange('DELIVERY')}>Doorstep delivery</button>
            </div>
          </fieldset>
          <div className="order-contact-fields">
            <label>Contact phone<input type="tel" autoComplete="tel" required maxLength={32} pattern="\+?[0-9() .-]{7,32}" value={customerPhone} onChange={(event) => onCustomerPhoneChange(event.target.value)} /></label>
            {fulfillmentType === 'DELIVERY' && <label className="delivery-address-field">Delivery address<textarea autoComplete="street-address" required maxLength={500} rows={3} value={deliveryAddress} onChange={(event) => onDeliveryAddressChange(event.target.value)} placeholder="Street, city, postcode" /></label>}
          </div>
          <div className="order-controls">
            <label>Quantity<input type="number" min="1" value={quantity} onChange={(event) => onQuantityChange(Math.max(1, Number(event.target.value)))} /></label>
            <button type="submit" className="primary-button" disabled={isSubmitting}>{isSubmitting ? 'Placing...' : 'Place order'}</button>
            <button type="button" className="icon-button" onClick={onCancel} aria-label="Cancel order">x</button>
          </div>
        </form>
      ) : <p className="order-placeholder">Choose an available coffee from the board to begin.</p>}
    </section>
  )
}