import type { EsimPlan, Product, Subscription } from '../types'

export const products: Product[] = [
  {
    id: 'nova-512',
    name: 'iPhone 18 Pro',
    description: 'A powerful Pro iPhone with generous storage for photos, apps, and 4K video.',
    storage: '512 GB',
    price: 899,
    finish: 'Burgundy',
    features: ['6.3-inch Super Retina XDR display', '48 MP Pro camera system', 'All-day battery'],
  },
  {
    id: 'nova-pro-1tb',
    name: 'iPhone 18 Pro Max',
    description: 'The largest Pro iPhone with maximum storage for demanding creative work.',
    storage: '1 TB',
    price: 1299,
    finish: 'Burgundy',
    features: ['6.9-inch Super Retina XDR display', '48 MP Pro camera system', 'Up to 45 hours video playback'],
  },
]

export const esimPlans: EsimPlan[] = [
  { id: 'limited', name: 'Everyday 2 GB', description: '2 GB of high-speed data per day', monthlyPrice: 14.99 },
  { id: 'unlimited', name: 'Unlimited', description: 'Unlimited data, calls, and texts', monthlyPrice: 29.99 },
]

export function createSubscription(product: Product, plan: EsimPlan): Subscription {
  const nextBillingDate = new Date()
  nextBillingDate.setMonth(nextBillingDate.getMonth() + 1)
  return {
    id: 'SUB-1001',
    product,
    plan,
    paymentStatus: 'pending',
    activationStatus: 'pending',
    deliveryStatus: 'pending',
    nextBillingDate: nextBillingDate.toISOString(),
  }
}
