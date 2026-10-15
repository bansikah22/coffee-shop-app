import { useCallback, useEffect, useState } from 'react'
import { request } from '../../shared/api/request'
import type { Coffee } from '../catalog/types'

export function useCoffeeOrder(coffees: Coffee[], ordersPath: string | null, setStatus: (status: string) => void, setError: (error: string) => void) {
  const [quantity, setQuantity] = useState(1)
  const [selectedCoffeeId, setSelectedCoffeeId] = useState<number | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [orders, setOrders] = useState<Order[]>([])
  const selectedCoffee = coffees.find((coffee) => coffee.id === selectedCoffeeId)

  const loadOrders = useCallback(async (path = ordersPath) => {
    if (!path) {
      setOrders([])
      return
    }
    try {
      setOrders(await request<Order[]>(path))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not load orders.')
    }
  }, [ordersPath, setError])

  useEffect(() => {
    const timer = window.setTimeout(() => { void loadOrders() }, 0)
    return () => window.clearTimeout(timer)
  }, [loadOrders])

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
      await loadOrders()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not place this order.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return { quantity, setQuantity, selectedCoffeeId, setSelectedCoffeeId, selectedCoffee, isSubmitting, placeOrder, orders, loadOrders }
}

export type Order = {
  id: number
  coffeeName: string
  quantity: number
  totalPrice: number
  createdAt: string
  customerEmail: string | null
}