export type Order={id:string;customerId:string;productName:string;storage:string;planName:string;total:number;devicePrice?:number;monthlyPrice?:number;status:string;createdAt:string};
export type TrackingSummary={orderId:string;customerId:string;workflowStatus:string;paymentStatus:string;activationStatus:string;fulfillmentStatus:string;billingStatus:string;updatedAt:string};
export type OrderTracking=Order&{payment:string;activation:string;fulfillment:string;billing:string;workflow:string;trackingUpdatedAt?:string};
export type ServiceHealth={name:string;status:'UP'|'DOWN'};

export type Payment={id:string;orderId:string;customerId:string;amount:number;currency:string;transactionReference:string;status:string;statusReason?:string|null;createdAt:string;updatedAt:string};
export type Activation={id:string;orderId:string;customerId:string;planId:string;iccid:string;status:string;statusReason?:string|null;requestedAt:string;statusChangedAt?:string|null};
export type Fulfillment={id:string;orderId:string;customerId:string;productId:string;trackingNumber:string;status:string;statusReason?:string|null;createdAt:string;statusChangedAt?:string|null};
export type Subscription={id:string;orderId:string;customerId:string;planName:string;monthlyAmount:number;status:string;statusReason?:string|null;createdAt:string;statusChangedAt?:string|null;billingStartedAt?:string|null;nextBillingAt?:string|null;billingDay?:number|null};

export type Step='payment'|'activation'|'fulfillment'|'billing';
export type Decision='approve'|'reject';
/** Everything the approval queues need, loaded together so billing can check the other steps. */
export type Operations={orders:Order[];payments:Payment[];activations:Activation[];fulfillments:Fulfillment[];subscriptions:Subscription[]};
export type LifecycleEvent={eventId:string;eventType:string;status:string;detail?:string|null;occurredAt:string};
/** Everything about one order for the single-order page; steps not started yet are null. */
export type OrderOperations={order:Order;payment:Payment|null;delivery:Fulfillment|null;activation:Activation|null;subscription:Subscription|null;events:LifecycleEvent[];workflow:string};

// Invoicing (invoice-service)
export type RunCounts={total:number;pending:number;invoiced:number;failed:number};
export type BillingRun={runId:number;executionId:number|null;runDate:string|null;trigger:string|null;status:string;exitDescription?:string|null;startedAt?:string|null;endedAt?:string|null;counts:RunCounts|null};
export type RunItem={id:number;runId:number;subscriptionId:string;orderId:string;customerId:string;planName:string;monthlyAmount:number;billingDay:number;periodStart:string;state:string;invoiceNumber?:string|null;message?:string|null;updatedAt:string};
export type PreviewLine={subscriptionId:string;orderId:string;customerId:string;planName:string;billingDay:number;periodStart:string;periodEnd:string;prorated:boolean;netAmount:number;vatAmount:number;totalAmount:number;existingInvoice?:string|null};
export type BillingPreview={runDate:string;subscriptions:number;total:number;currency:string;lines:PreviewLine[]};
export type BillingScheduleSettings={enabled:boolean;runTime:string;zone:string;nextRunAt?:string|null;updatedAt:string};
export type Invoice={invoiceNumber:string;runId:number;orderId:string;customerId:string;planName:string;periodStart:string;periodEnd:string;prorated:boolean;netAmount:number;vatRate:number;vatAmount:number;totalAmount:number;currency:string;status:string;paymentReference?:string|null;failureReason?:string|null;issuedAt:string};
