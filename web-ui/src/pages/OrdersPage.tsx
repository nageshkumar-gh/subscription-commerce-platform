import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'
import { describeOrder, type OrderProgress } from '../orders/orderProgress'
import type { PlacedOrder } from '../types'

type Row = { order: PlacedOrder; progress: OrderProgress }

export function OrdersPage() {
  const { user, accessToken } = useAuth()
  const [rows, setRows] = useState<Row[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user || !accessToken) return
    let cancelled = false
    async function load() {
      try {
        const orders = await api.getCustomerOrders(accessToken!)
        // The list only needs each order's lifecycle events; the detail page loads the full step records.
        const loaded = await Promise.all(orders.map(async (order) => ({ order, progress: describeOrder({ order, payment: null, delivery: null, activation: null, billing: null, events: await api.getOrderEvents(order.id, accessToken!).catch(() => []) }) })))
        if (!cancelled) { setRows(loaded.sort((a, b) => b.order.createdAt.localeCompare(a.order.createdAt))); setError('') }
      } catch (caught) {
        if (!cancelled) setError(caught instanceof ApiError ? caught.message : 'Your orders are unavailable. Please try again.')
      }
    }
    load()
    const id = setInterval(load, 15000)
    return () => { cancelled = true; clearInterval(id) }
  }, [user, accessToken])

  if (error && !rows) return <StateMessage title="Orders unavailable" tone="error"><p>{error}</p></StateMessage>
  if (!rows) return <StateMessage title="Loading your orders…" />
  if (!rows.length) return <StateMessage title="No orders yet"><p>Your orders will appear here after checkout.</p><Link className="button" to="/products">View products</Link></StateMessage>

  return (
    <section>
      <div className="page-heading"><p className="eyebrow">My orders</p><h1>Your orders</h1><p>Select an order number to see its full progress and what happens next.</p></div>
      <ul className="order-list">
        {rows.map(({ order, progress }) => (
          <li key={order.id} className="order-card">
            <div>
              <Link className="order-card__number" to={`/orders/${order.id}`}>Order {order.id}</Link>
              <h2>{order.productName} · {order.storage}</h2>
              <p>{order.planName} eSIM · €{Number(order.monthlyPrice).toFixed(2)}/month · placed {new Date(order.createdAt).toLocaleDateString()}</p>
            </div>
            <div className="order-card__status">
              <span className={`pill pill--${progress.tone}`}>{progress.headline}</span>
              <ol className="mini-steps" aria-label="Progress">{progress.steps.map((step) => <li key={step.key} className={`mini-steps__item mini-steps__item--${step.state}`} title={`${step.label}: ${step.summary}`}>{step.label}</li>)}</ol>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
