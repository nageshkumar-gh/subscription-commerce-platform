import { products, createSubscription } from '../data/mockData'
import type { AuthSession, Credentials, EsimPlan, Order, Product, ProfileUpdate, Registration, Subscription, User } from '../types'

const delay = (milliseconds = 250) => new Promise((resolve) => setTimeout(resolve, milliseconds))

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message)
  }
}

export const api = {
  async getProducts(): Promise<Product[]> {
    await delay()
    return products
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

  async takePayment(order: Order): Promise<Subscription> {
    const workflow = await fetch('/api/workflows/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ orderId: order.id, customerId: order.customer.id, productId: order.product.id, planId: order.plan.id, planName: order.plan.name, total: order.total, monthlyAmount: order.plan.monthlyPrice }) })
    if (!workflow.ok) throw new ApiError('The order workflow could not be started.', workflow.status)
    return createSubscription(order.product, order.plan)
  },
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
