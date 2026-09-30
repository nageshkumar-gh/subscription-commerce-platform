import type { LifecycleEvent, OrderDetails } from '../types'

export type StepState = 'done' | 'current' | 'upcoming' | 'failed'
export type StepView = { key: 'payment' | 'delivery' | 'activation' | 'billing'; label: string; state: StepState; status: string | null; summary: string; reason?: string | null }
export type Tone = 'success' | 'progress' | 'error'
export type OrderProgress = { steps: StepView[]; headline: string; tone: Tone; nextAction: string; cancelled?: boolean }

const formatDate = (value?: string | null) => (value ? new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '')
const euro = (value: number) => `€${Number(value).toFixed(2)}`

/** Latest status the workflow published for a step, used when the step's own service has no record (or is down). */
function latestEvent(events: LifecycleEvent[], eventType: string) {
  return [...events].filter((event) => event.eventType === eventType).sort((a, b) => a.occurredAt.localeCompare(b.occurredAt)).at(-1)
}

/**
 * Describes an order for the customer. Steps run in order: payment, then delivery of the phone, then eSIM activation
 * (only once the phone has arrived), then billing.
 */
export function describeOrder({ order, payment, delivery, activation, billing, events }: OrderDetails): OrderProgress {
  const status = (record: { status: string } | null, eventType: string) => record?.status ?? latestEvent(events, eventType)?.status ?? null
  const reason = (record: { statusReason?: string | null } | null, eventType: string) => record?.statusReason ?? latestEvent(events, eventType)?.detail ?? null

  const paymentStatus = status(payment, 'PAYMENT')
  const deliveryStatus = status(delivery, 'FULFILLMENT')
  const activationStatus = status(activation, 'ESIM_ACTIVATION')
  const billingStatus = status(billing, 'BILLING')

  const steps: StepView[] = [
    {
      key: 'payment', label: 'Payment', status: paymentStatus, reason: reason(payment, 'PAYMENT'),
      ...(paymentStatus === 'COMPLETED' ? { state: 'done', summary: `${euro(payment?.amount ?? order.total)} paid${payment ? ` · ref ${payment.transactionReference}` : ''}` }
        : paymentStatus === 'FAILED' || paymentStatus === 'REFUNDED' ? { state: 'failed', summary: 'Your payment was declined. No charge was made.' }
          : { state: 'current', summary: 'We are confirming your payment.' }),
    },
    {
      key: 'delivery', label: 'Delivery', status: deliveryStatus, reason: reason(delivery, 'FULFILLMENT'),
      ...(deliveryStatus === 'DELIVERED' ? { state: 'done', summary: `Delivered${delivery?.deliveredAt ? ` on ${formatDate(delivery.deliveredAt)}` : ''}` }
        : deliveryStatus === 'FAILED' ? { state: 'failed', summary: 'We could not deliver your phone.' }
          : deliveryStatus === 'DISPATCHED' ? { state: 'current', summary: `On its way${delivery ? ` · tracking ${delivery.trackingNumber}` : ''}` }
            : deliveryStatus === 'PREPARING' ? { state: 'current', summary: 'Your phone is being packed.' }
              : deliveryStatus ? { state: 'current', summary: 'Your order is with our warehouse.' }
                : { state: 'upcoming', summary: 'Starts once your payment is confirmed.' }),
    },
    {
      key: 'activation', label: 'eSIM activation', status: activationStatus, reason: reason(activation, 'ESIM_ACTIVATION'),
      ...(activationStatus === 'ACTIVE' ? { state: 'done', summary: `Active${activation?.activatedAt ? ` since ${formatDate(activation.activatedAt)}` : ''}${activation ? ` · ICCID ${activation.iccid}` : ''}` }
        : activationStatus === 'FAILED' ? { state: 'failed', summary: 'Your eSIM could not be activated.' }
          : activationStatus === 'ACTIVATING' ? { state: 'current', summary: 'Your eSIM is being activated on the network.' }
            : activationStatus ? { state: 'current', summary: 'Your eSIM activation is queued.' }
              : { state: 'upcoming', summary: 'Starts once your phone has been delivered.' }),
    },
    {
      key: 'billing', label: 'Monthly billing', status: billingStatus, reason: reason(billing, 'BILLING'),
      ...(billingStatus === 'ACTIVE' ? { state: 'done', summary: `${euro(billing?.monthlyAmount ?? order.monthlyPrice)}/month${billing?.nextBillingAt ? ` · next bill ${formatDate(billing.nextBillingAt)}` : ''}` }
        : billingStatus === 'REJECTED' ? { state: 'failed', summary: 'Your plan billing could not be started.' }
          : billingStatus === 'SUSPENDED' || billingStatus === 'CANCELLED' ? { state: 'failed', summary: `Your plan is ${billingStatus.toLowerCase()}.` }
            : billingStatus ? { state: 'current', summary: 'Your plan is being set up.' }
              : { state: 'upcoming', summary: `${euro(order.monthlyPrice)}/month, starting once your eSIM is active.` }),
    },
  ]

  const lastWorkflowEvent = latestEvent(events, 'ORDER_WORKFLOW')
  if (lastWorkflowEvent?.status === 'CANCELLED' || lastWorkflowEvent?.status === 'CANCELLING') {
    return { steps, tone: 'error', cancelled: true, headline: 'Order cancelled', nextAction: `This order was cancelled${lastWorkflowEvent.detail ? `: ${lastWorkflowEvent.detail}` : '.'} Any payment taken will be refunded.` }
  }
  const failed = steps.find((step) => step.state === 'failed')
  if (failed) return { steps, tone: 'error', headline: `${failed.label} failed`, nextAction: `${failed.summary}${failed.reason ? ` Reason: ${failed.reason}.` : ''} Please contact support if you need help.` }
  if (lastWorkflowEvent?.status === 'FAILED') return { steps, tone: 'error', headline: 'Order could not be completed', nextAction: `${lastWorkflowEvent.detail ?? 'Something went wrong while processing your order.'} Please contact support.` }

  const current = steps.find((step) => step.state !== 'done')
  if (!current) return { steps, tone: 'success', headline: 'Subscription active', nextAction: 'All set. Your phone is delivered, your eSIM is active and your plan is running.' }
  if (current.state === 'upcoming') current.state = 'current'
  const next: Record<StepView['key'], string> = {
    payment: 'We are confirming your payment. This usually takes a few minutes.',
    delivery: deliveryStatus === 'DISPATCHED' ? `Your phone is on its way${delivery ? `. Track it with ${delivery.trackingNumber}` : ''}.` : 'We are preparing your phone for delivery.',
    activation: 'Your phone has arrived. We are activating your eSIM now.',
    billing: 'Almost done. We are setting up your monthly plan.',
  }
  return { steps, tone: 'progress', headline: current.label === 'Payment' ? 'Payment pending' : `${current.label} in progress`, nextAction: next[current.key] }
}
