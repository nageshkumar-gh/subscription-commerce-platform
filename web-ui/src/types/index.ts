export type Product = {
  id: string
  sku: string
  name: string
  description: string
  storage: string
  price: number
  finish: string
  features: string[]
  active: boolean
}

export type EsimPlan = {
  id: string
  code: string
  name: string
  description: string
  monthlyPrice: number
  active: boolean
}

export type User = {
  id: string
  name: string
  email: string
  phone?: string
}

export type Credentials = { email: string; password: string }
export type Registration = Credentials & { name: string; phone: string }
export type ProfileUpdate = { name: string; email: string; phone: string }
export type AuthSession = { accessToken: string; user: User }

export type Order = {
  id: string
  product: Product
  plan: EsimPlan
  customer: User
  total: number
  createdAt: string
}

/** An order as stored by order-service, used for the customer's order list and detail page. */
export type PlacedOrder = {
  id: string
  customerId: string
  productId: string
  productName: string
  storage: string
  planId: string
  planName: string
  devicePrice: number
  monthlyPrice: number
  total: number
  status: string
  createdAt: string
}

export type PaymentRecord = { id: string; status: string; statusReason?: string | null; amount: number; currency: string; transactionReference: string; updatedAt: string }
export type DeliveryRecord = { id: string; status: string; statusReason?: string | null; trackingNumber: string; createdAt: string; statusChangedAt?: string | null; deliveredAt?: string | null }
export type ActivationRecord = { id: string; status: string; statusReason?: string | null; iccid: string; requestedAt: string; statusChangedAt?: string | null; activatedAt?: string | null }
/** A subscription as listed by billing-service for a customer. */
export type BillingSubscription = { id: string; orderId: string; planName: string; monthlyAmount: number; status: string; billingStartedAt?: string | null; nextBillingAt?: string | null }
export type BillingRecord = { id: string; status: string; statusReason?: string | null; planName: string; monthlyAmount: number; billingStartedAt?: string | null; nextBillingAt?: string | null }
export type LifecycleEvent = { eventId: string; eventType: string; status: string; detail?: string | null; occurredAt: string }

/** Everything known about one order; a step that has not started yet is null. */
export type OrderDetails = {
  order: PlacedOrder
  payment: PaymentRecord | null
  delivery: DeliveryRecord | null
  activation: ActivationRecord | null
  billing: BillingRecord | null
  events: LifecycleEvent[]
}
