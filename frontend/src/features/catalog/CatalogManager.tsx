import type { SyntheticEvent } from 'react'
import type { Coffee, CoffeeForm } from './types'

type CatalogManagerProps = {
  coffees: Coffee[]
  form: CoffeeForm
  editingId: number | null
  isSubmitting: boolean
  onFormChange: (form: CoffeeForm) => void
  onSubmit: (event: SyntheticEvent<HTMLFormElement, SubmitEvent>) => void
  onCancelEdit: () => void
  onEdit: (coffee: Coffee) => void
  onDelete: (coffee: Coffee) => void
}

export function CatalogManager({
  coffees,
  form,
  editingId,
  isSubmitting,
  onFormChange,
  onSubmit,
  onCancelEdit,
  onEdit,
  onDelete,
}: CatalogManagerProps) {
  return (
    <section className="manager" id="catalog-manager" aria-labelledby="manager-title">
      <div className="manager-intro">
        <p className="eyebrow">Catalog management</p>
        <h2 id="manager-title">Keep the board sharp.</h2>
        <p>Add a new roast, change availability, or remove a finished offering.</p>
      </div>
      <form className="coffee-form" onSubmit={onSubmit}>
        <label>Name<input required maxLength={100} value={form.name} onChange={(event) => onFormChange({ ...form, name: event.target.value })} /></label>
        <label>Price<input required min="0" step="0.01" type="number" value={form.price} onChange={(event) => onFormChange({ ...form, price: Number(event.target.value) })} /></label>
        <label className="description-field">Description<textarea required maxLength={500} value={form.description} onChange={(event) => onFormChange({ ...form, description: event.target.value })} /></label>
        <label className="availability-toggle"><input type="checkbox" checked={form.available} onChange={(event) => onFormChange({ ...form, available: event.target.checked })} />Available for ordering</label>
        <div className="form-actions">
          <button className="primary-button" disabled={isSubmitting} type="submit">{editingId === null ? 'Add coffee' : 'Save changes'}</button>
          {editingId !== null && <button className="secondary-button" type="button" onClick={onCancelEdit}>Cancel edit</button>}
        </div>
      </form>
      <div className="management-list" aria-label="Catalog entries">
        {coffees.map((coffee) => (
          <div className="management-row" key={coffee.id}>
            <div><strong>{coffee.name}</strong><span>{coffee.available ? 'On board' : 'Paused'} · ${coffee.price.toFixed(2)}</span></div>
            <div className="row-actions">
              <button type="button" onClick={() => onEdit(coffee)}>Edit</button>
              <button type="button" className="danger-button" onClick={() => onDelete(coffee)}>Remove</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}