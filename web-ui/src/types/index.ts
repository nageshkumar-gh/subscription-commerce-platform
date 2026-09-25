export type Product = {
  id: string
  name: string
  description: string
  storage: '512 GB' | '1 TB'
  price: number
  finish: string
  features: string[]
}

export type EsimPlan = {
  id: 'limited' | 'unlimited'
  name: string
  description: string
  monthlyPrice: number
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

export type Status = 'pending' | 'complete'

export type Subscription = {
  id: string
  product: Product
  plan: EsimPlan
  paymentStatus: Status
  activationStatus: Status
  deliveryStatus: Status
  nextBillingDate: string
}

export type Order = {
  id: string
  product: Product
  plan: EsimPlan
  customer: User
  total: number
  createdAt: string
}
