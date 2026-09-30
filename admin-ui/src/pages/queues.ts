import type{Activation,Fulfillment,Operations,Order,Payment,Step,Subscription}from'../types';

export type Check={label:string;status:string;ok:boolean};
export type QueueRow={
  key:string;orderId:string;customerId:string;item:string;reference:string;status:string;statusReason?:string|null;updatedAt?:string|null;
  /** Label of the button that approves the next step, or null when nothing can be approved. */
  approveLabel:string|null;
  /** Why approval is currently not allowed, e.g. billing waiting on delivery. */
  blocked?:string;
  canReject:boolean;
  checks?:Check[];
};
export type Queue={step:Step;tab:string;title:string;description:string;rows:QueueRow[]};

const nextPaymentStep:Record<string,string>={PENDING:'Approve payment'};
const nextActivationStep:Record<string,string>={QUEUED:'Start activation',ACTIVATING:'Mark active'};
const nextDeliveryStep:Record<string,string>={RECEIVED:'Mark preparing',PREPARING:'Mark dispatched',DISPATCHED:'Mark delivered'};
const billingOpen=['WAITING_FOR_FULFILLMENT','WAITING_FOR_ACTIVATION','READY_FOR_BILLING'];

export const needsAction=(row:QueueRow)=>row.approveLabel!==null&&!row.blocked;
export const describeItem=(order:Order|undefined|null,fallback:string)=>order?`${order.productName} · ${order.storage} · ${order.planName}`:fallback;

// One builder per step, shared by the approval tabs and the single-order page.
export const paymentRow=(p:Payment,item:string):QueueRow=>({key:p.id,orderId:p.orderId,customerId:p.customerId,item,reference:`${p.transactionReference} · ${p.currency} ${Number(p.amount).toFixed(2)}`,status:p.status,statusReason:p.statusReason,updatedAt:p.updatedAt,approveLabel:nextPaymentStep[p.status]??null,canReject:p.status==='PENDING'});
export const deliveryRow=(f:Fulfillment,item:string):QueueRow=>({key:f.id,orderId:f.orderId,customerId:f.customerId,item,reference:f.trackingNumber,status:f.status,statusReason:f.statusReason,updatedAt:f.statusChangedAt??f.createdAt,approveLabel:nextDeliveryStep[f.status]??null,canReject:f.status in nextDeliveryStep});
export const activationRow=(a:Activation,item:string):QueueRow=>({key:a.id,orderId:a.orderId,customerId:a.customerId,item,reference:`ICCID ${a.iccid}`,status:a.status,statusReason:a.statusReason,updatedAt:a.statusChangedAt??a.requestedAt,approveLabel:nextActivationStep[a.status]??null,canReject:a.status in nextActivationStep});
export function billingRow(s:Subscription,item:string,steps:{payment?:Payment|null;delivery?:Fulfillment|null;activation?:Activation|null}):QueueRow{
  const checks:Check[]=[
    {label:'Payment',status:steps.payment?.status??'UNKNOWN',ok:steps.payment?.status==='COMPLETED'},
    {label:'Delivery',status:steps.delivery?.status??'UNKNOWN',ok:steps.delivery?.status==='DELIVERED'},
    {label:'eSIM',status:steps.activation?.status??'UNKNOWN',ok:steps.activation?.status==='ACTIVE'},
  ];
  const open=billingOpen.includes(s.status),waiting=checks.filter(check=>!check.ok).map(check=>check.label.toLowerCase());
  const blocked=!open?undefined:waiting.length?`Waiting for ${waiting.join(', ')}`:s.status!=='READY_FOR_BILLING'?'Billing service is still reconciling':undefined;
  const next=s.nextBillingAt?`next invoice ${new Date(s.nextBillingAt).toLocaleDateString()}`:'not billing yet';
  return{key:s.orderId,orderId:s.orderId,customerId:s.customerId,item,reference:`€${Number(s.monthlyAmount).toFixed(2)}/month · ${next}`,status:s.status,statusReason:s.statusReason,updatedAt:s.statusChangedAt??s.createdAt,approveLabel:open?'Start billing':null,blocked,canReject:open,checks};
}

export function buildQueues(data:Operations):Queue[]{
  const orders=new Map(data.orders.map(order=>[order.id,order]));
  const byOrder=<T extends{orderId:string}>(records:T[])=>new Map(records.map(record=>[record.orderId,record]));
  const payments=byOrder(data.payments),activations=byOrder(data.activations),fulfillments=byOrder(data.fulfillments);
  return[
    {step:'payment',tab:'Payments',title:'Payment approvals',description:'Confirm or decline the simulated card payment. Rejecting fails the order.',
      rows:data.payments.map(p=>paymentRow(p,describeItem(orders.get(p.orderId),'Order')))},
    {step:'fulfillment',tab:'Delivery',title:'Device delivery',description:'Advance each shipment: received → preparing → dispatched → delivered.',
      rows:data.fulfillments.map(f=>deliveryRow(f,describeItem(orders.get(f.orderId),f.productId)))},
    {step:'activation',tab:'Activation',title:'eSIM activation',description:'Created once the phone is delivered. Advance each activation: queued → activating → active.',
      rows:data.activations.map(a=>activationRow(a,describeItem(orders.get(a.orderId),a.planId)))},
    {step:'billing',tab:'Billing',title:'Billing start',description:'Recurring billing can only start once payment, delivery and eSIM activation are all complete. Invoices are raised from the next billing date.',
      rows:data.subscriptions.map(s=>billingRow(s,describeItem(orders.get(s.orderId),s.planName),{payment:payments.get(s.orderId),delivery:fulfillments.get(s.orderId),activation:activations.get(s.orderId)}))},
  ];
}
