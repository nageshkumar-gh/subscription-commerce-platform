import { Link, useLocation } from 'react-router-dom'
import { StateMessage } from '../components/StateMessage'
import type { Order, Subscription } from '../types'

export function OrderConfirmationPage() {
  const state = useLocation().state as { order?: Order; subscription?: Subscription } | null
  if (!state?.order || !state.subscription) return <StateMessage title="No recent order"><Link to="/products">Browse products</Link></StateMessage>
  return (
    <StateMessage title="Your subscription is confirmed" tone="success">
      <p>Order <strong>{state.order.id}</strong> has been paid and activated.</p>
      <Link className="button" to="/subscription" state={{ subscription: state.subscription }}>View subscription</Link>
    </StateMessage>
  )
}
