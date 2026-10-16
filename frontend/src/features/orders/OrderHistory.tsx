import type { Order } from './useCoffeeOrder'

type OrderHistoryProps = {
  orders: Order[]
  isAdmin: boolean
  onRefresh: () => void
  onMarkReady?: (orderId: number) => void
  onMarkCompleted?: (orderId: number) => void
  markingOrderReady?: number | null
}

export function OrderHistory({ orders, isAdmin, onRefresh, onMarkReady, onMarkCompleted, markingOrderReady }: OrderHistoryProps) {
  return (
    <section className="order-history" aria-labelledby="order-history-title">
      <div className="section-heading">
        <div><p className="eyebrow">{isAdmin ? 'Store activity' : 'Your account'}</p><h2 id="order-history-title">{isAdmin ? 'All orders' : 'Your orders'}</h2></div>
        <button className="refresh-button" type="button" onClick={onRefresh}>Refresh</button>
      </div>
      {orders.length === 0 ? <p className="order-placeholder">No orders yet.</p> : (
        <div className="orders-table-wrap"><table className="orders-table">
          <thead><tr><th>Order</th>{isAdmin && <th>Customer</th>}<th>Contact / destination</th><th>Coffee</th><th>Quantity</th><th>Total</th><th>Placed</th><th>Fulfillment</th><th>Status</th>{isAdmin && <th>Action</th>}</tr></thead>
          <tbody>{orders.map((order) => <tr key={order.id}>
            <td>#{order.id}</td>{isAdmin && <td>{order.customerEmail ?? 'Legacy order'}</td>}<td className="order-contact">{order.customerPhone}{order.fulfillmentType === 'DELIVERY' && order.deliveryAddress && <address>{order.deliveryAddress}</address>}</td><td>{order.coffeeName}</td><td>{order.quantity}</td><td>${order.totalPrice.toFixed(2)}</td><td>{new Date(order.createdAt).toLocaleString()}</td><td>{order.fulfillmentType === 'DELIVERY' ? 'Delivery' : 'Pickup'}</td><td>{order.status === 'OUT_FOR_DELIVERY' ? 'Out for delivery' : order.status === 'READY' ? 'Ready for collection' : order.status === 'COMPLETED' ? 'Completed' : 'Preparing'}</td>{isAdmin && <td>{order.status === 'PLACED' && <button type="button" className="refresh-button" disabled={markingOrderReady === order.id} onClick={() => onMarkReady?.(order.id)}>{markingOrderReady === order.id ? 'Updating...' : order.fulfillmentType === 'DELIVERY' ? 'Dispatch' : 'Ready for collection'}</button>}{(order.status === 'READY' || order.status === 'OUT_FOR_DELIVERY') && <button type="button" className="refresh-button" disabled={markingOrderReady === order.id} onClick={() => onMarkCompleted?.(order.id)}>{markingOrderReady === order.id ? 'Updating...' : order.fulfillmentType === 'DELIVERY' ? 'Mark delivered' : 'Mark collected'}</button>}</td>}
          </tr>)}</tbody>
        </table></div>
      )}
    </section>
  )
}