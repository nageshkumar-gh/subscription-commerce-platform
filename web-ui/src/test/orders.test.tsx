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

  function backend(orders: PlacedOrder[]) {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/orders?customerId=customer-1') return json(orders.filter((o) => o.customerId === 'customer-1'))
      const match = orders.find((o) => url === `/api/orders/${o.id}`)
      if (match) return json(match)
      if (url.startsWith('/api/payments?orderId=order-1')) return json(paid)
      if (url.includes('/events')) return json([{ eventId: 'e1', eventType: 'PAYMENT', status: 'COMPLETED', occurredAt: '2026-09-29T10:05:00Z' }])
      return json({ message: 'not found' }, 404)
    }))
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
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/subscriptions?customerId=customer-1&status=ACTIVE') return json([{ id: 'sub-1', orderId: 'order-1', planName: 'Unlimited', monthlyAmount: 29.99, status: 'ACTIVE', billingStartedAt: '2026-09-30T10:00:00Z', nextBillingAt: '2026-10-30T10:00:00Z' }])
      if (url === '/api/orders?customerId=customer-1') return json([order])
      return json({ message: 'not found' }, 404)
    }))
    window.history.pushState({}, '', '/subscription')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Active subscriptions' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'iPhone 18 Pro Max · 1 TB' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Order order-1' })).toHaveAttribute('href', '/orders/order-1')
  })

  it('lets the customer cancel an order before the subscription is active', async () => {
    const posts: { url: string; body: unknown }[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (init?.method === 'POST') { posts.push({ url, body: JSON.parse(String(init.body)) }); return json({ status: 'CANCELLED' }, 202) }
      if (url === '/api/orders/order-1') return json(order)
      if (url.startsWith('/api/payments?orderId=order-1')) return json(paid)
      return json({ message: 'not found' }, 404)
    }))
    window.history.pushState({}, '', '/orders/order-1')
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: 'Cancel order' }))
    await user.selectOptions(screen.getByLabelText('Why are you cancelling?'), 'I found a better price')
    await user.click(screen.getByRole('button', { name: 'Confirm cancellation' }))
    expect(posts).toEqual([{ url: '/api/workflows/orders/order-1/cancel', body: { reason: 'Customer: I found a better price' } }])
  })

  it('hides cancellation once the subscription is active', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/orders/order-1') return json(order)
      if (url.startsWith('/api/subscriptions?orderId=order-1')) return json({ id: 'sub-1', status: 'ACTIVE', planName: 'Unlimited', monthlyAmount: 29.99 })
      return json({ message: 'not found' }, 404)
    }))
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
