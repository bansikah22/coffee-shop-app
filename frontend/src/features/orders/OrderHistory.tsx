import type { Order } from './useCoffeeOrder'

type OrderHistoryProps = {
  orders: Order[]
  isAdmin: boolean
  onRefresh: () => void
}

export function OrderHistory({ orders, isAdmin, onRefresh }: OrderHistoryProps) {
  return (
    <section className="order-history" aria-labelledby="order-history-title">
      <div className="section-heading">
        <div><p className="eyebrow">{isAdmin ? 'Store activity' : 'Your account'}</p><h2 id="order-history-title">{isAdmin ? 'All orders' : 'Your orders'}</h2></div>
        <button className="refresh-button" type="button" onClick={onRefresh}>Refresh</button>
      </div>
      {orders.length === 0 ? <p className="order-placeholder">No orders yet.</p> : (
        <div className="orders-table-wrap"><table className="orders-table">
          <thead><tr><th>Order</th>{isAdmin && <th>Customer</th>}<th>Coffee</th><th>Quantity</th><th>Total</th><th>Placed</th></tr></thead>
          <tbody>{orders.map((order) => <tr key={order.id}>
            <td>#{order.id}</td>{isAdmin && <td>{order.customerEmail ?? 'Legacy order'}</td>}<td>{order.coffeeName}</td><td>{order.quantity}</td><td>${order.totalPrice.toFixed(2)}</td><td>{new Date(order.createdAt).toLocaleString()}</td>
          </tr>)}</tbody>
        </table></div>
      )}
    </section>
  )
}