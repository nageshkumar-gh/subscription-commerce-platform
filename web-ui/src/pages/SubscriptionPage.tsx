import { Link, useLocation } from 'react-router-dom'
import { StateMessage } from '../components/StateMessage'
import type { Status, Subscription } from '../types'

function StatusItem({ label, status }: { label: string; status: Status }) {
  return <li><span>{label}</span><strong className={`status status--${status}`}>{status === 'complete' ? 'Complete' : 'Pending'}</strong></li>
}

export function SubscriptionPage() {
  const subscription = (useLocation().state as { subscription?: Subscription } | null)?.subscription
  if (!subscription) return <StateMessage title="No active subscription"><p>Your subscription will appear here after checkout.</p><Link className="button" to="/products">View products</Link></StateMessage>
  return (
    <section className="narrow-page">
      <div className="page-heading"><p className="eyebrow">Subscription {subscription.id}</p><h1>{subscription.product.name} · {subscription.product.storage}</h1><p>{subscription.plan.name} eSIM · €{subscription.plan.monthlyPrice.toFixed(2)}/month</p><p>Next billing date: {new Date(subscription.nextBillingDate).toLocaleDateString()}</p></div>
      <ol className="status-list">
        <StatusItem label="Payment" status={subscription.paymentStatus} />
        <StatusItem label="Activation" status={subscription.activationStatus} />
        <StatusItem label="Delivery" status={subscription.deliveryStatus} />
      </ol>
    </section>
  )
}
