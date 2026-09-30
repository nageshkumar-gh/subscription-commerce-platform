import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'
import { describeOrder } from '../orders/orderProgress'
import type { OrderDetails } from '../types'

const stepStateLabel = { done: 'Complete', current: 'In progress', upcoming: 'Not started', failed: 'Failed' }
const eventLabels: Record<string, string> = { ORDER_WORKFLOW: 'Order', PAYMENT: 'Payment', FULFILLMENT: 'Delivery', ESIM_ACTIVATION: 'eSIM', BILLING: 'Billing' }
const humanize = (value: string) => value.replaceAll('_', ' ').toLowerCase()

export function OrderDetailPage() {
  const { orderId = '' } = useParams()
  const { user, accessToken } = useAuth()
  const [details, setDetails] = useState<OrderDetails | null | undefined>(undefined)
  const [error, setError] = useState('')

  // Bumped after a cancellation so the page reloads straight away rather than on the next poll.
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!user || !accessToken) return
    let cancelled = false
    async function load() {
      try {
        const loaded = await api.getOrderDetails(orderId, accessToken!)
        if (!cancelled) { setDetails(loaded); setError('') }
      } catch (caught) {
        if (!cancelled) setError(caught instanceof ApiError ? caught.message : 'This order is unavailable. Please try again.')
      }
    }
    load()
    const id = setInterval(load, 10000)
    return () => { cancelled = true; clearInterval(id) }
  }, [orderId, user, accessToken, reloadKey])

  if (error && !details) return <StateMessage title="Order unavailable" tone="error"><p>{error}</p><Link to="/orders">Back to your orders</Link></StateMessage>
  if (details === undefined) return <StateMessage title="Loading your order…" />
  if (details === null) return <StateMessage title="Order not found"><p>We could not find this order on your account.</p><Link className="button" to="/orders">Back to your orders</Link></StateMessage>

  const { order, events } = details
  const progress = describeOrder(details)
  // Customers may cancel until monthly billing is active; after that it is a subscription, not an order in progress.
  const cancellable = order.status !== 'CANCELLED' && !progress.cancelled && details.billing?.status !== 'ACTIVE'

  return (
    <section className="order-detail">
      <Link to="/orders">← All orders</Link>
      <div className="page-heading">
        <p className="eyebrow">Order {order.id}</p>
        <h1>{order.productName} · {order.storage}</h1>
        <p>Placed {new Date(order.createdAt).toLocaleString()}</p>
      </div>

      <div className={`next-action next-action--${progress.tone}`} role="status">
        <p className="eyebrow">What happens next</p>
        <h2>{progress.headline}</h2>
        <p>{progress.nextAction}</p>
      </div>

      <div className="order-detail__grid">
        <ol className="timeline" aria-label="Order progress">
          {progress.steps.map((step) => (
            <li key={step.key} className={`timeline__step timeline__step--${step.state}`}>
              <div className="timeline__head"><h3>{step.label}</h3><span className={`status status--${step.state}`}>{stepStateLabel[step.state]}</span></div>
              <p>{step.summary}</p>
              {step.status && <small>Status: {humanize(step.status)}</small>}
              {step.reason && <small className="timeline__reason">Note: {step.reason}</small>}
            </li>
          ))}
        </ol>

        <aside className="summary-card">
          <h2>Order summary</h2>
          <dl className="details">
            <dt>Phone</dt><dd>{order.productName} · {order.storage}</dd>
            <dt>Plan</dt><dd>{order.planName} eSIM</dd>
            <dt>Device price</dt><dd>€{Number(order.devicePrice).toFixed(2)}</dd>
            <dt>Monthly plan</dt><dd>€{Number(order.monthlyPrice).toFixed(2)}/month</dd>
            <dt>Paid today</dt><dd><strong>€{Number(order.total).toFixed(2)}</strong></dd>
            {details.payment && <><dt>Payment reference</dt><dd>{details.payment.transactionReference}</dd></>}
            {details.delivery && <><dt>Tracking number</dt><dd>{details.delivery.trackingNumber}</dd></>}
            {details.activation && <><dt>eSIM ICCID</dt><dd>{details.activation.iccid}</dd></>}
            {details.billing?.billingStartedAt && <><dt>Billing started</dt><dd>{new Date(details.billing.billingStartedAt).toLocaleDateString()}</dd></>}
            {details.billing?.nextBillingAt && <><dt>Next bill</dt><dd>{new Date(details.billing.nextBillingAt).toLocaleDateString()}</dd></>}
          </dl>
        </aside>
      </div>

      {cancellable && <CancelOrder orderId={order.id} token={accessToken!} onCancelled={() => setReloadKey((key) => key + 1)} />}

      {events.length > 0 && (
        <details className="history">
          <summary>Order history ({events.length} updates)</summary>
          <ul>
            {[...events].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)).map((event) => (
              <li key={event.eventId}><time>{new Date(event.occurredAt).toLocaleString()}</time><span>{eventLabels[event.eventType] ?? humanize(event.eventType)}: {humanize(event.status)}</span>{event.detail && <small>{event.detail}</small>}</li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

const cancelReasons = ['I changed my mind', 'I found a better price', 'Delivery is taking too long', 'I ordered the wrong phone or plan', 'Other']

function CancelOrder({ orderId, token, onCancelled }: { orderId: string; token: string; onCancelled: () => void }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState(cancelReasons[0])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function confirm() {
    setBusy(true)
    setError('')
    try {
      await api.cancelOrder(orderId, reason, token)
      setOpen(false)
      onCancelled()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Your order could not be cancelled. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="cancel-order">
      <div>
        <h2>Cancel this order</h2>
        <p>You can cancel until your subscription is active. Any payment taken will be refunded.</p>
      </div>
      {!open ? <button className="button button--danger" onClick={() => setOpen(true)}>Cancel order</button> : (
        <div className="cancel-order__confirm">
          <label>Why are you cancelling?
            <select value={reason} onChange={(event) => setReason(event.target.value)}>{cancelReasons.map((option) => <option key={option}>{option}</option>)}</select>
          </label>
          <div className="cancel-order__buttons">
            <button className="button button--danger" disabled={busy} onClick={confirm}>{busy ? 'Cancelling…' : 'Confirm cancellation'}</button>
            <button className="button button--quiet" disabled={busy} onClick={() => setOpen(false)}>Keep my order</button>
          </div>
        </div>
      )}
      {error && <p className="cancel-order__error" role="alert">{error}</p>}
    </section>
  )
}
