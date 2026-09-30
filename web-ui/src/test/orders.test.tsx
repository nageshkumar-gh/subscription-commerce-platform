import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { describeOrder } from '../orders/orderProgress'
import type { OrderDetails, PlacedOrder } from '../types'

const order: PlacedOrder = { id: 'order-1', customerId: 'customer-1', productId: 'phone-2', productName: 'iPhone 18 Pro Max', storage: '1 TB', planId: 'plan-2', planName: 'Unlimited', devicePrice: 1299, monthlyPrice: 29.99, total: 1328.99, status: 'PENDING_PAYMENT', createdAt: '2026-09-29T10:00:00Z' }
const nothingStarted: OrderDetails = { order, payment: null, delivery: null, activation: null, billing: null, events: [] }
const paid = { id: 'pay-1', status: 'COMPLETED', amount: 1328.99, currency: 'EUR', transactionReference: 'PAY-ABC', updatedAt: '2026-09-29T10:05:00Z' }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('order progress', () => {
  it('waits for payment first', () => {
    const progress = describeOrder(nothingStarted)
    expect(progress.headline).toBe('Payment pending')
    expect(progress.steps.map((step) => [step.key, step.state])).toEqual([['payment', 'current'], ['delivery', 'upcoming'], ['activation', 'upcoming'], ['billing', 'upcoming']])
  })

  it('delivers the phone before activating the eSIM', () => {
    const progress = describeOrder({ ...nothingStarted, payment: paid, delivery: { id: 'ful-1', status: 'DISPATCHED', trackingNumber: 'TRACK-1', createdAt: order.createdAt } })
    expect(progress.headline).toBe('Delivery in progress')
    expect(progress.nextAction).toContain('TRACK-1')
    expect(progress.steps.find((step) => step.key === 'activation')?.summary).toBe('Starts once your phone has been delivered.')
  })

  it('explains a rejected step with the operator reason', () => {
    const progress = describeOrder({ ...nothingStarted, payment: { ...paid, status: 'FAILED', statusReason: 'Card declined by issuer' } })
    expect(progress.tone).toBe('error')
    expect(progress.nextAction).toContain('Card declined by issuer')
  })

  it('falls back to lifecycle events when a step service has no record', () => {
    const progress = describeOrder({ ...nothingStarted, events: [{ eventId: 'e1', eventType: 'PAYMENT', status: 'COMPLETED', occurredAt: '2026-09-29T10:05:00Z' }, { eventId: 'e2', eventType: 'FULFILLMENT', status: 'PREPARING', occurredAt: '2026-09-29T10:06:00Z' }] })
    expect(progress.steps.map((step) => step.state)).toEqual(['done', 'current', 'upcoming', 'upcoming'])
  })

  it('is complete once billing is active', () => {
    const progress = describeOrder({ ...nothingStarted, payment: paid, delivery: { id: 'ful-1', status: 'DELIVERED', trackingNumber: 'TRACK-1', createdAt: order.createdAt }, activation: { id: 'act-1', status: 'ACTIVE', iccid: '8944123', requestedAt: order.createdAt }, billing: { id: 'sub-1', status: 'ACTIVE', planName: 'Unlimited', monthlyAmount: 29.99, nextBillingAt: '2026-10-29T10:00:00Z' } })
    expect(progress.tone).toBe('success')
    expect(progress.steps.every((step) => step.state === 'done')).toBe(true)
  })
})

describe('customer order pages', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
    localStorage.setItem('subscription-auth-session', JSON.stringify({ accessToken: 'token', user: { id: 'customer-1', name: 'Test Customer', email: 'test@example.com' } }))
  })

  const events = [{ eventId: 'e1', eventType: 'PAYMENT', status: 'COMPLETED', occurredAt: '2026-09-29T10:05:00Z' }]
  /** Stands in for the storefront API: requires the customer's token and only serves that customer's orders. */
  function backend(orders: PlacedOrder[], extra: (url: string) => Response | undefined = () => undefined) {
    const posts: { url: string; body: unknown }[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (!url.startsWith('/api/me/')) return json({ message: 'not found' }, 404)
      if ((init?.headers as Record<string, string> | undefined)?.Authorization !== 'Bearer token') return json({ message: 'unauthorized' }, 401)
      if (init?.method === 'POST') { posts.push({ url, body: JSON.parse(String(init.body)) }); return json({ status: 'CANCELLED' }, 202) }
      const own = orders.filter((o) => o.customerId === 'customer-1')
      if (url === '/api/me/orders') return json(own)
      const custom = extra(url)
      if (custom) return custom
      const match = own.find((o) => url === `/api/me/orders/${o.id}`)
      if (match) return json({ order: match, payment: paid, delivery: null, activation: null, billing: null, events })
      if (own.some((o) => url === `/api/me/orders/${o.id}/events`)) return json(events)
      return json({ message: 'not found' }, 404)
    }))
    return posts
  }

  it('lists orders with a link that opens the order detail page', async () => {
    backend([order])
    window.history.pushState({}, '', '/orders')
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('link', { name: 'Order order-1' }))
    expect(await screen.findByRole('heading', { name: 'iPhone 18 Pro Max · 1 TB' })).toBeInTheDocument()
    expect(screen.getByText('What happens next')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Delivery in progress' })).toBeInTheDocument()
    expect(screen.getByText('PAY-ABC')).toBeInTheDocument()
  })

  it('shows a cancelled order with its reason', () => {
    const progress = describeOrder({ ...nothingStarted, payment: paid, events: [{ eventId: 'e9', eventType: 'ORDER_WORKFLOW', status: 'CANCELLED', detail: 'Customer request (refund requested)', occurredAt: '2026-09-29T11:00:00Z' }] })
    expect(progress.headline).toBe('Order cancelled')
    expect(progress.nextAction).toContain('Customer request')
  })

  it('lists only active subscriptions under My subscription', async () => {
    backend([order], (url) => url === '/api/me/subscriptions' ? json([{ id: 'sub-1', orderId: 'order-1', planName: 'Unlimited', monthlyAmount: 29.99, status: 'ACTIVE', billingStartedAt: '2026-09-30T10:00:00Z', nextBillingAt: '2026-10-30T10:00:00Z' }]) : undefined)
    window.history.pushState({}, '', '/subscription')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Active subscriptions' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'iPhone 18 Pro Max · 1 TB' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Order order-1' })).toHaveAttribute('href', '/orders/order-1')
  })

  it('lets the customer cancel an order before the subscription is active', async () => {
    const posts = backend([order])
    window.history.pushState({}, '', '/orders/order-1')
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: 'Cancel order' }))
    await user.selectOptions(screen.getByLabelText('Why are you cancelling?'), 'I found a better price')
    await user.click(screen.getByRole('button', { name: 'Confirm cancellation' }))
    expect(posts).toEqual([{ url: '/api/me/orders/order-1/cancel', body: { reason: 'I found a better price' } }])
  })

  it('hides cancellation once the subscription is active', async () => {
    backend([order], (url) => url === '/api/me/orders/order-1' ? json({ order, payment: paid, delivery: null, activation: null, billing: { id: 'sub-1', status: 'ACTIVE', planName: 'Unlimited', monthlyAmount: 29.99 }, events: [] }) : undefined)
    window.history.pushState({}, '', '/orders/order-1')
    render(<App />)
    expect(await screen.findByText('What happens next')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
  })

  it("does not show another customer's order", async () => {
    backend([{ ...order, id: 'order-2', customerId: 'someone-else' }])
    window.history.pushState({}, '', '/orders/order-2')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Order not found' })).toBeInTheDocument()
  })
})
