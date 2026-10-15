import { type SyntheticEvent, useCallback, useState } from 'react'
import { request } from '../../shared/api/request'
import { emptyCoffee, type Coffee, type CoffeeForm } from './types'

export function useCoffeeCatalog(setStatus: (status: string) => void, setError: (error: string) => void) {
  const [coffees, setCoffees] = useState<Coffee[]>([])
  const [form, setForm] = useState<CoffeeForm>(emptyCoffee)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const loadCoffees = useCallback(async () => {
    try {
      setError('')
      const catalog = await request<Coffee[]>('/coffees')
      setCoffees(catalog)
      setStatus(`${catalog.filter((coffee) => coffee.available).length} coffees ready to order`)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not load the coffee board.')
      setStatus('Catalog unavailable')
    }
  }, [setError, setStatus])

  function resetForm() {
    setForm(emptyCoffee)
    setEditingId(null)
  }

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
    } finally {
      setIsSubmitting(false)
    }
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

  return { coffees, form, setForm, editingId, isSubmitting, loadCoffees, resetForm, startEdit, saveCoffee, deleteCoffee }
}