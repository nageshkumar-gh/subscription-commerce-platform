import { Link, useLocation } from 'react-router-dom'
import { StateMessage } from '../components/StateMessage'
import type { Order } from '../types'

export function OrderConfirmationPage() {
  const order = (useLocation().state as { order?: Order } | null)?.order
  if (!order) return <StateMessage title="No recent order"><Link to="/products">Browse products</Link></StateMessage>
  return (
    <StateMessage title="Thanks, your order is placed" tone="success">
      <p>Order <strong>{order.id}</strong> is confirmed. We will confirm your payment, deliver your phone, then activate your eSIM.</p>
      <Link className="button" to={`/orders/${order.id}`}>Track your order</Link>
    </StateMessage>
  )
}
