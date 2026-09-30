import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'
import type { BillingSubscription, PlacedOrder } from '../types'

type Row = { subscription: BillingSubscription; order?: PlacedOrder }
const formatDate = (value?: string | null) => (value ? new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—')

/** Only subscriptions whose monthly billing is running; orders still in progress live under My orders. */
export function SubscriptionPage() {
  const { user, accessToken } = useAuth()
  const [rows, setRows] = useState<Row[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user || !accessToken) return
    let cancelled = false
    Promise.all([api.getActiveSubscriptions(accessToken), api.getCustomerOrders(accessToken).catch(() => [])])
      .then(([subscriptions, orders]) => {
        if (cancelled) return
        const byId = new Map(orders.map((order) => [order.id, order]))
        setRows(subscriptions.map((subscription) => ({ subscription, order: byId.get(subscription.orderId) })))
      })
      .catch((caught) => { if (!cancelled) setError(caught instanceof ApiError ? caught.message : 'Your subscriptions are unavailable. Please try again.') })
    return () => { cancelled = true }
  }, [user, accessToken])

  if (error) return <StateMessage title="Subscriptions unavailable" tone="error"><p>{error}</p></StateMessage>
  if (!rows) return <StateMessage title="Loading your subscriptions…" />
  if (!rows.length) return <StateMessage title="No active subscriptions"><p>A subscription appears here once your phone is delivered and your eSIM is active.</p><Link className="button" to="/orders">Check your orders</Link></StateMessage>

  const monthly = rows.reduce((sum, { subscription }) => sum + Number(subscription.monthlyAmount), 0)
  return (
    <section>
      <div className="page-heading"><p className="eyebrow">My subscription</p><h1>Active subscriptions</h1><p>{rows.length} active · €{monthly.toFixed(2)} per month in total</p></div>
      <ul className="order-list">
        {rows.map(({ subscription, order }) => (
          <li key={subscription.id} className="order-card">
            <div>
              <span className="pill pill--success">Active</span>
              <h2>{order ? `${order.productName} · ${order.storage}` : subscription.planName}</h2>
              <p>{subscription.planName} eSIM · €{Number(subscription.monthlyAmount).toFixed(2)}/month</p>
              <p>Since {formatDate(subscription.billingStartedAt)} · next bill {formatDate(subscription.nextBillingAt)}</p>
            </div>
            <Link className="order-card__number" to={`/orders/${subscription.orderId}`}>Order {subscription.orderId}</Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
