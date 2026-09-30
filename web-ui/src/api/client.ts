import type { AuthSession, BillingSubscription, Credentials, EsimPlan, LifecycleEvent, Order, OrderDetails, PlacedOrder, Product, ProfileUpdate, Registration, User } from '../types'

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

  // Orders go through the storefront API (/api/me), which acts only for the signed-in customer and prices from the catalogue.

  async createOrder(product: Product, plan: EsimPlan, customer: User, token: string): Promise<Order> {
    const saved = await send<{ id: string; total: number; createdAt: string }>('/api/me/orders', token, { productId: product.id, planId: plan.id }, 'Order service could not create the order.')
    return { id: saved.id, product, plan, customer, total: saved.total, createdAt: saved.createdAt }
  },

  async takePayment(order: Order, token: string): Promise<void> {
    await send(`/api/me/orders/${encodeURIComponent(order.id)}/checkout`, token, {}, 'The order workflow could not be started.')
  },

  async getCustomerOrders(token: string): Promise<PlacedOrder[]> {
    const orders = await getJson<PlacedOrder[]>('/api/me/orders', token, 'Your orders are unavailable. Please try again.')
    if (!Array.isArray(orders)) throw new ApiError('Order service returned an invalid response.', 502)
    return orders
  },

  /** Cancels an order before its subscription is active; payment is voided or refunded. */
  async cancelOrder(orderId: string, reason: string, token: string): Promise<void> {
    await send(`/api/me/orders/${encodeURIComponent(orderId)}/cancel`, token, { reason }, 'Your order could not be cancelled. Please try again.')
  },

  /** The customer's subscriptions whose monthly billing is running. */
  async getActiveSubscriptions(token: string): Promise<BillingSubscription[]> {
    const subscriptions = await getJson<BillingSubscription[]>('/api/me/subscriptions', token, 'Your subscriptions are unavailable. Please try again.')
    return Array.isArray(subscriptions) ? subscriptions : []
  },

  /** Lifecycle history for one order; an order with no events yet returns an empty list. */
  async getOrderEvents(orderId: string, token: string): Promise<LifecycleEvent[]> {
    const events = await getJson<LifecycleEvent[]>(`/api/me/orders/${encodeURIComponent(orderId)}/events`, token, 'Order tracking is unavailable.', true)
    return Array.isArray(events) ? events : []
  },

  /** The order with each step's record (null until started). Null when the order does not exist or is not the customer's. */
  async getOrderDetails(orderId: string, token: string): Promise<OrderDetails | null> {
    const details = await getJson<OrderDetails | null>(`/api/me/orders/${encodeURIComponent(orderId)}`, token, 'This order is unavailable. Please try again.', true)
    return details ? { ...details, events: Array.isArray(details.events) ? details.events : [] } : null
  },
}

/** Authenticated GET; with `notFoundAsNull` a 404 resolves to null instead of throwing. */
async function getJson<T>(url: string, token: string, unavailable: string, notFoundAsNull = false): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { headers: authorization(token) })
  } catch {
    throw new ApiError(unavailable, 503)
  }
  if (response.status === 401) throw new ApiError('Your session has expired. Please log in again.', 401)
  if (notFoundAsNull && response.status === 404) return null as T
  if (!response.ok) throw new ApiError(unavailable, response.status)
  return response.json() as Promise<T>
}

/** Authenticated JSON POST that surfaces the service's message on failure. */
async function send<T = unknown>(url: string, token: string, body: unknown, failure: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...authorization(token) }, body: JSON.stringify(body) })
  } catch {
    throw new ApiError('We could not reach our order service. Please try again.', 503)
  }
  const parsed = await response.json().catch(() => null) as (T & { message?: string }) | null
  if (response.status === 401) throw new ApiError('Your session has expired. Please log in again.', 401)
  if (!response.ok) throw new ApiError(parsed?.message ?? failure, response.status)
  return parsed as T
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
