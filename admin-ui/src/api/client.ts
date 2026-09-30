import{apiFetch}from'../auth';
import type{Activation,BillingPreview,BillingRun,BillingScheduleSettings,Decision,Invoice,RunItem,Fulfillment,LifecycleEvent,Operations,Order,OrderOperations,OrderTracking,Payment,ServiceHealth,Step,Subscription,TrackingSummary}from'../types';
async function json<T>(url:string):Promise<T>{const r=await apiFetch(url);if(!r.ok)throw new Error(`${url} returned ${r.status}`);return r.json()}
export async function getTracking():Promise<OrderTracking[]>{const[orders,summaries]=await Promise.all([json<Order[]>('/api/orders'),json<TrackingSummary[]>('/api/tracking/orders')]);const byOrder=new Map(summaries.map(summary=>[summary.orderId,summary]));return orders.map(order=>{const summary=byOrder.get(order.id);return{...order,payment:summary?.paymentStatus??'NOT_STARTED',activation:summary?.activationStatus??'NOT_STARTED',fulfillment:summary?.fulfillmentStatus??'NOT_STARTED',billing:summary?.billingStatus??'NOT_STARTED',workflow:summary?.workflowStatus??'NOT_STARTED',trackingUpdatedAt:summary?.updatedAt}})}
const services=['order','orchestration','tracking','payment','network','fulfillment','billing','invoice'] as const;
export async function getServiceHealth():Promise<ServiceHealth[]>{return Promise.all(services.map(async name=>{try{const value=await json<{status:string}>(`/ops/health/${name}`);return{name,status:value.status==='UP'?'UP':'DOWN'} as ServiceHealth}catch{return{name,status:'DOWN'} as ServiceHealth}}))}

/** Loads every approval queue; a service that is down yields an empty list plus its name in `unavailable`. */
export async function getOperations():Promise<{data:Operations;unavailable:string[]}>{
  const sources={orders:'/api/orders',payments:'/api/payments',activations:'/api/activations',fulfillments:'/api/fulfillments',subscriptions:'/api/subscriptions'} as const;
  const keys=Object.keys(sources) as (keyof typeof sources)[];
  const results=await Promise.allSettled(keys.map(key=>json<unknown>(sources[key])));
  const data={} as Record<keyof typeof sources,unknown[]>;const unavailable:string[]=[];
  results.forEach((result,i)=>{const ok=result.status==='fulfilled'&&Array.isArray(result.value);data[keys[i]]=ok?result.value as unknown[]:[];if(!ok)unavailable.push(keys[i])});
  return{data:{orders:data.orders as Order[],payments:data.payments as Payment[],activations:data.activations as Activation[],fulfillments:data.fulfillments as Fulfillment[],subscriptions:data.subscriptions as Subscription[]},unavailable};
}

const decisionPaths:Record<Step,string>={payment:'/api/payments',activation:'/api/activations',fulfillment:'/api/fulfillments',billing:'/api/subscriptions'};
/** `key` is the record ID, except billing which is addressed by order ID. */
export async function decide(step:Step,key:string,decision:Decision,reason:string):Promise<void>{
  const trimmed=reason.trim();
  const r=await apiFetch(`${decisionPaths[step]}/${encodeURIComponent(key)}/${decision}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:trimmed||null})});
  if(!r.ok){const body=await r.json().catch(()=>null) as {message?:string}|null;throw new Error(body?.message??`The ${step} service returned ${r.status}`)}
}

/** GET JSON where 404 means "not started yet" (null); a step service being down also yields null so the page still renders. */
async function optional<T>(url:string):Promise<T|null>{try{const r=await apiFetch(url);return r.ok?await r.json() as T:null}catch{return null}}

/** Loads one order with every step record, its lifecycle history and the workflow state. Null if the order does not exist. */
export async function getOrderOperations(orderId:string):Promise<OrderOperations|null>{
  const id=encodeURIComponent(orderId),byOrder=`?orderId=${id}`;
  const r=await apiFetch(`/api/orders/${id}`);
  if(r.status===404)return null;
  if(!r.ok)throw new Error(`Order service returned ${r.status}`);
  const order=await r.json() as Order;
  const[payment,delivery,activation,subscription,events,workflow]=await Promise.all([
    optional<Payment>(`/api/payments${byOrder}`),optional<Fulfillment>(`/api/fulfillments${byOrder}`),optional<Activation>(`/api/activations${byOrder}`),optional<Subscription>(`/api/subscriptions${byOrder}`),
    optional<LifecycleEvent[]>(`/api/tracking/orders/${id}/events`),optional<{status:string}>(`/api/workflows/orders/${id}`),
  ]);
  return{order,payment,delivery,activation,subscription,events:Array.isArray(events)?events:[],workflow:workflow?.status??'NOT_STARTED'};
}

async function post(url:string,body:unknown,failure:string):Promise<void>{
  const r=await apiFetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  if(!r.ok){const b=await r.json().catch(()=>null) as {message?:string}|null;throw new Error(b?.message??`${failure} (${r.status})`)}
}
/** Cancels an order that is still in progress; the workflow compensates the steps already taken. */
export const cancelOrder=(orderId:string,reason:string)=>post(`/api/workflows/orders/${encodeURIComponent(orderId)}/cancel`,{reason:reason.trim()},'The order could not be cancelled');
/** Stops recurring billing on a completed order. */
export const cancelSubscription=(orderId:string)=>post(`/api/subscriptions/${encodeURIComponent(orderId)}/cancel`,{},'The subscription could not be cancelled');

export async function getActiveSubscriptions(customerId:string):Promise<Subscription[]>{
  const r=await apiFetch(`/api/subscriptions?customerId=${encodeURIComponent(customerId.trim())}&status=ACTIVE`);
  if(!r.ok)throw new Error(`Billing service returned ${r.status}`);
  const body=await r.json();
  return Array.isArray(body)?body:[];
}

/** Sends JSON and returns the parsed response, surfacing the service's message on failure. */
async function send<T>(url:string,method:'POST'|'PUT',body:unknown,failure:string):Promise<T>{
  const r=await apiFetch(url,{method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  const parsed=await r.json().catch(()=>null) as (T&{message?:string})|null;
  if(!r.ok)throw new Error(parsed?.message??`${failure} (${r.status})`);
  return parsed as T;
}
export const changeBillingDay=(orderId:string,billingDay:number)=>send<unknown>(`/api/subscriptions/${encodeURIComponent(orderId)}/billing-day`,'PUT',{billingDay},'The billing day could not be changed');

// Invoicing
export const getBillingRuns=()=>json<BillingRun[]>('/api/billing-runs');
export const getRunItems=(runId:number)=>json<RunItem[]>(`/api/billing-runs/${runId}/items`);
export const previewBilling=(runDate:string)=>json<BillingPreview>(`/api/billing-runs/preview?runDate=${encodeURIComponent(runDate)}`);
export const startBillingRun=(runDate:string)=>send<BillingRun>('/api/billing-runs','POST',{runDate},'The invoicing run could not start');
export const getBillingSchedule=()=>json<BillingScheduleSettings>('/api/billing-schedule');
export const saveBillingSchedule=(enabled:boolean,runTime:string)=>send<BillingScheduleSettings>('/api/billing-schedule','PUT',{enabled,runTime},'The schedule could not be saved');
export const searchInvoices=(customerId:string)=>json<Invoice[]>(`/api/invoices?customerId=${encodeURIComponent(customerId.trim())}`);
