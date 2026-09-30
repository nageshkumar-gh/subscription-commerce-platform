import type { ActivationRecord, AuthSession, BillingRecord, BillingSubscription, Credentials, DeliveryRecord, EsimPlan, LifecycleEvent, Order, OrderDetails, PaymentRecord, PlacedOrder, Product, ProfileUpdate, Registration, User } from '../types'

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message)
  }
}

export const api = {
  async getCatalogue(): Promise<{ products: Product[]; plans: EsimPlan[] }> {
    try {
      const [productsResponse, plansResponse] = await Promise.all([fetch('/api/products'), fetch('/api/esim-plans')])
      if (!productsResponse.ok || !plansResponse.ok) throw new ApiError('The product catalogue is unavailable. Please try again.', Math.max(productsResponse.status, plansResponse.status))
      const [products, plans] = await Promise.all([productsResponse.json() as Promise<Product[]>, plansResponse.json() as Promise<EsimPlan[]>])
      if (!Array.isArray(products) || !Array.isArray(plans)) throw new ApiError('Product service returned an invalid response.', 502)
      return { products, plans }
    } catch (error) {
      if (error instanceof ApiError) throw error
      throw new ApiError('Product service is unavailable. Please try again.', 503)
    }
  },

  async login(credentials: Credentials): Promise<AuthSession> {
    return authenticate('/api/auth/login', credentials)
  },

  async register(registration: Registration): Promise<AuthSession> {
    return authenticate('/api/auth/register', registration)
  },

  async updateCustomer(token: string, profile: ProfileUpdate): Promise<User> {
    let response: Response
    try {
      response = await fetch('/api/customers/me', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...authorization(token) },
        body: JSON.stringify({ ...profile, active: true }),
      })
    } catch {
      throw new ApiError('Customer service is unavailable. Make sure it is running on port 8080.', 503)
    }

    const body = await response.json().catch(() => null) as { id?: string; name?: string; email?: string; phone?: string; message?: string } | null
    if (!response.ok) {
      throw new ApiError(body?.message ?? 'Profile update failed. Please try again.', response.status)
    }
    if (!body?.id || !body.name || !body.email) throw new ApiError('Customer service returned an invalid response.', 502)
    return { id: body.id, name: body.name, email: body.email, phone: body.phone }
  },

  async deleteCustomer(token: string): Promise<void> {
    let response: Response
    try {
      response = await fetch('/api/customers/me', { method: 'DELETE', headers: authorization(token) })
    } catch {
      throw new ApiError('Customer service is unavailable. Please try again.', 503)
    }
    if (!response.ok) {
      const body = await response.json().catch(() => null) as { message?: string } | null
      throw new ApiError(body?.message ?? 'Profile deletion failed. Please try again.', response.status)
    }
  },

  async createOrder(product: Product, plan: EsimPlan, customer: User): Promise<Order> {
    const response = await fetch('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ customerId: customer.id, productId: product.id, productName: product.name, storage: product.storage, planId: plan.id, planName: plan.name, devicePrice: product.price, monthlyPrice: plan.monthlyPrice }) })
    if (!response.ok) throw new ApiError('Order service could not create the order.', response.status)
    const saved = await response.json() as { id: string; total: number; createdAt: string }
    return { id: saved.id, product, plan, customer, total: saved.total, createdAt: saved.createdAt }
  },

  async takePayment(order: Order): Promise<void> {
    const workflow = await fetch('/api/workflows/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ orderId: order.id, customerId: order.customer.id, productId: order.product.id, planId: order.plan.id, planName: order.plan.name, total: order.total, monthlyAmount: order.plan.monthlyPrice }) })
    if (!workflow.ok) throw new ApiError('The order workflow could not be started.', workflow.status)
  },

  async getCustomerOrders(customerId: string): Promise<PlacedOrder[]> {
    const orders = await getJson<PlacedOrder[]>(`/api/orders?customerId=${encodeURIComponent(customerId)}`, 'Your orders are unavailable. Please try again.')
    if (!Array.isArray(orders)) throw new ApiError('Order service returned an invalid response.', 502)
    return orders
  },

  /** Cancels an order before its subscription is active; payment is voided or refunded. */
  async cancelOrder(orderId: string, reason: string): Promise<void> {
    let response: Response
    try {
      response = await fetch(`/api/workflows/orders/${encodeURIComponent(orderId)}/cancel`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason }) })
    } catch {
      throw new ApiError('We could not reach our order service. Please try again.', 503)
    }
    if (!response.ok) {
      const body = await response.json().catch(() => null) as { message?: string } | null
      throw new ApiError(body?.message ?? 'Your order could not be cancelled. Please try again.', response.status)
    }
  },

  /** The customer's subscriptions whose monthly billing is running. */
  async getActiveSubscriptions(customerId: string): Promise<BillingSubscription[]> {
    const subscriptions = await getJson<BillingSubscription[]>(`/api/subscriptions?customerId=${encodeURIComponent(customerId)}&status=ACTIVE`, 'Your subscriptions are unavailable. Please try again.')
    return Array.isArray(subscriptions) ? subscriptions : []
  },

  /** Lifecycle history for one order; an order with no events yet returns an empty list. */
  async getOrderEvents(orderId: string): Promise<LifecycleEvent[]> {
    const events = await getJson<LifecycleEvent[]>(`/api/tracking/orders/${encodeURIComponent(orderId)}/events`, 'Order tracking is unavailable.', true)
    return Array.isArray(events) ? events : []
  },

  /**
   * Loads the order plus each step's record. Steps that have not started yet (404) are null. Returns null when
   * the order does not exist or belongs to another customer.
   */
  async getOrderDetails(orderId: string, customerId: string): Promise<OrderDetails | null> {
    const order = await getJson<PlacedOrder>(`/api/orders/${encodeURIComponent(orderId)}`, 'This order is unavailable. Please try again.', true)
    if (!order || order.customerId !== customerId) return null
    const byOrder = `?orderId=${encodeURIComponent(orderId)}`
    // A step service being down should not hide the rest of the order, so each lookup degrades to null.
    const optional = <T,>(url: string) => getJson<T>(url, '', true).catch(() => null)
    const [payment, delivery, activation, billing, events] = await Promise.all([
      optional<PaymentRecord>(`/api/payments${byOrder}`),
      optional<DeliveryRecord>(`/api/fulfillments${byOrder}`),
      optional<ActivationRecord>(`/api/activations${byOrder}`),
      optional<BillingRecord>(`/api/subscriptions${byOrder}`),
      this.getOrderEvents(orderId).catch(() => []),
    ])
    return { order, payment, delivery, activation, billing, events }
  },
}

/** GET JSON; with `notFoundAsNull` a 404 resolves to null instead of throwing. */
async function getJson<T>(url: string, unavailable: string, notFoundAsNull = false): Promise<T> {
  let response: Response
  try {
    response = await fetch(url)
  } catch {
    throw new ApiError(unavailable, 503)
  }
  if (notFoundAsNull && response.status === 404) return null as T
  if (!response.ok) throw new ApiError(unavailable, response.status)
  return response.json() as Promise<T>
}

function authorization(token: string) {
  return { Authorization: `Bearer ${token}` }
}

async function authenticate(path: string, credentials: Credentials | Registration): Promise<AuthSession> {
  let response: Response
  try {
    response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials),
    })
  } catch {
    throw new ApiError('Customer service is unavailable. Make sure it is running on port 8080.', 503)
  }
  const body = await response.json().catch(() => null) as { accessToken?: string; customer?: User; message?: string } | null
  if (!response.ok) throw new ApiError(body?.message ?? 'Authentication failed. Please try again.', response.status)
  if (!body?.accessToken || !body.customer?.id) throw new ApiError('Customer service returned an invalid response.', 502)
  return { accessToken: body.accessToken, user: body.customer }
}
