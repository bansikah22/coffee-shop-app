import { useCallback, useEffect, useState } from 'react'
import { request } from '../../shared/api/request'
import type { Coffee } from '../catalog/types'

export function useCoffeeOrder(coffees: Coffee[], ordersPath: string | null, setStatus: (status: string) => void, setError: (error: string) => void) {
  const [quantity, setQuantity] = useState(1)
  const [fulfillmentType, setFulfillmentType] = useState<FulfillmentType>('PICKUP')
  const [customerPhone, setCustomerPhone] = useState('')
  const [deliveryAddress, setDeliveryAddress] = useState('')
  const [selectedCoffeeId, setSelectedCoffeeId] = useState<number | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [markingOrderReady, setMarkingOrderReady] = useState<number | null>(null)
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

  useEffect(() => {
    if (!successMessage) return
    const timer = window.setTimeout(() => setSuccessMessage(''), 5000)
    return () => window.clearTimeout(timer)
  }, [successMessage])

  async function placeOrder() {
    if (selectedCoffeeId === null) return
    setIsSubmitting(true)
    setError('')
    setSuccessMessage('')
    try {
      const order = await request<{ id: number; coffeeName: string; quantity: number; totalPrice: number }>('/orders', {
        method: 'POST', body: JSON.stringify({ coffeeId: selectedCoffeeId, quantity, fulfillmentType, customerPhone, deliveryAddress: fulfillmentType === 'DELIVERY' ? deliveryAddress : null }),
      })
      setSuccessMessage(`${order.coffeeName} order #${order.id} placed successfully.`)
      setStatus(`Order placed: ${order.quantity} ${order.coffeeName} for $${order.totalPrice.toFixed(2)}`)
      setSelectedCoffeeId(null)
      setQuantity(1)
      setFulfillmentType('PICKUP')
      setDeliveryAddress('')
      await loadOrders()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not place this order.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function markOrderReady(orderId: number) {
    setMarkingOrderReady(orderId)
    setError('')
    try {
      const updated = await request<Order>(`/admin/orders/${orderId}/ready`, { method: 'PATCH' })
      setStatus(updated.fulfillmentType === 'DELIVERY' ? `Order #${orderId} is out for delivery.` : `Order #${orderId} is ready for collection.`)
      await loadOrders()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not update this order.')
    } finally {
      setMarkingOrderReady(null)
    }
  }

  async function markOrderCompleted(orderId: number) {
    setMarkingOrderReady(orderId)
    setError('')
    try {
      const updated = await request<Order>(`/admin/orders/${orderId}/complete`, { method: 'PATCH' })
      setStatus(updated.fulfillmentType === 'DELIVERY' ? `Order #${orderId} is marked delivered.` : `Order #${orderId} is marked collected.`)
      await loadOrders()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not update this order.')
    } finally {
      setMarkingOrderReady(null)
    }
  }

  return { quantity, setQuantity, fulfillmentType, setFulfillmentType, customerPhone, setCustomerPhone,
    deliveryAddress, setDeliveryAddress, selectedCoffeeId, setSelectedCoffeeId, selectedCoffee,
    isSubmitting, placeOrder, orders, loadOrders, markingOrderReady, markOrderReady, markOrderCompleted,
    successMessage, dismissSuccessMessage: () => setSuccessMessage('') }
}

export type FulfillmentType = 'PICKUP' | 'DELIVERY'

export type Order = {
  id: number
  coffeeName: string
  quantity: number
  totalPrice: number
  createdAt: string
  customerEmail: string | null
  status: 'PLACED' | 'READY' | 'OUT_FOR_DELIVERY' | 'COMPLETED'
  fulfillmentType: FulfillmentType
  customerPhone: string
  deliveryAddress: string | null
}