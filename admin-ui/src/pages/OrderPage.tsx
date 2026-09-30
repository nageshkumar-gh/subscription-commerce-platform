import{useCallback,useEffect,useState}from'react';
import{cancelOrder,cancelSubscription,changeBillingDay,decide,getOrderOperations}from'../api/client';
import{Badge}from'../components/Badge';
import type{Decision,OrderOperations,Step}from'../types';
import{DecisionControls}from'./ApprovalQueue';
import{activationRow,billingRow,deliveryRow,describeItem,paymentRow,type QueueRow}from'./queues';

const closed=['COMPLETED','FAILED','CANCELLING','CANCELLED'];
const eventLabels:Record<string,string>={ORDER_WORKFLOW:'Order',PAYMENT:'Payment',FULFILLMENT:'Delivery',ESIM_ACTIVATION:'eSIM',BILLING:'Billing'};

/** Everything about one order on a single page: each step with its approve/reject controls, cancellation and history. */
export function OrderPage({orderId}:{orderId:string}){
  const[data,setData]=useState<OrderOperations|null|undefined>(undefined),[error,setError]=useState('');
  const load=useCallback(async()=>{try{setData(await getOrderOperations(orderId));setError('')}catch(e){setError(e instanceof Error?e.message:'The order could not be loaded.')}},[orderId]);
  useEffect(()=>{load();const id=setInterval(load,10000);return()=>clearInterval(id)},[load]);
  const onDecide=useCallback(async(step:Step,row:QueueRow,decision:Decision,reason:string)=>{await decide(step,row.key,decision,reason);await load()},[load]);

  const back=<a href="#/" className="back">← Back to console</a>;
  if(error&&!data)return <>{back}<div className="state error">{error}</div></>;
  if(data===undefined)return <>{back}<div className="state">Loading order…</div></>;
  if(data===null)return <>{back}<div className="state">Order {orderId} was not found.</div></>;

  const{order,payment,delivery,activation,subscription,events,workflow}=data;
  const item=describeItem(order,order.productName);
  const steps:{step:Step;label:string;row:QueueRow|null;waiting:string}[]=[
    {step:'payment',label:'1. Payment',row:payment&&paymentRow(payment,item),waiting:'Created when the order workflow starts.'},
    {step:'fulfillment',label:'2. Delivery',row:delivery&&deliveryRow(delivery,item),waiting:'Created once payment is approved.'},
    {step:'activation',label:'3. eSIM activation',row:activation&&activationRow(activation,item),waiting:'Created once the phone is delivered.'},
    {step:'billing',label:'4. Billing',row:subscription&&billingRow(subscription,item,{payment,delivery,activation}),waiting:'Created once the eSIM is active.'},
  ];
  // Steps can only be actioned while the workflow runs; the order itself can be cancelled at any point before it completes.
  const inProgress=workflow!=='NOT_STARTED'&&!closed.includes(workflow);
  const cancellable=order.status!=='CANCELLED'&&!['COMPLETED','CANCELLING','CANCELLED'].includes(workflow);
  const subscriptionCancellable=workflow==='COMPLETED'&&!!subscription&&['ACTIVE','SUSPENDED'].includes(subscription.status);

  return <article className="order-page">
    {back}
    <div className="order-page__head">
      <div><p className="eyebrow">Order</p><h2>{order.id}</h2><p className="muted">{item} · customer {order.customerId} · placed {new Date(order.createdAt).toLocaleString()}</p></div>
      <div className="order-page__state"><span className="muted">Workflow</span><Badge value={workflow}/></div>
    </div>

    <div className="order-page__grid">
      <section className="step-list" aria-label="Order steps">
        {steps.map(({step,label,row,waiting})=><div key={step} className="step-card">
          <div className="step-card__head"><h3>{label}</h3>{row?<Badge value={row.status}/>:<Badge value="NOT_STARTED"/>}</div>
          {row?<>
            <p className="muted">{row.reference}{row.updatedAt&&` · updated ${new Date(row.updatedAt).toLocaleString()}`}</p>
            {row.statusReason&&<p className="reason">“{row.statusReason}”</p>}
            {row.checks&&<ul className="checks checks--inline">{row.checks.map(check=><li key={check.label} className={check.ok?'ok':'waiting'}>{check.ok?'✓':'…'} {check.label}</li>)}</ul>}
            {inProgress&&<DecisionControls step={step} row={row} onDecide={onDecide}/>}
            {step==='billing'&&subscription&&['ACTIVE','SUSPENDED'].includes(subscription.status)&&<BillingDayControl orderId={order.id} current={subscription.billingDay??null} nextBillingAt={subscription.nextBillingAt??null} onSaved={load}/>}
          </>:<p className="muted">{waiting}</p>}
        </div>)}
      </section>

      <aside className="side">
        <section className="panel">
          <h3>Order summary</h3>
          <dl className="details">
            {order.devicePrice!=null&&<><dt>Device</dt><dd>€{Number(order.devicePrice).toFixed(2)}</dd></>}
            {order.monthlyPrice!=null&&<><dt>Plan</dt><dd>{order.planName} · €{Number(order.monthlyPrice).toFixed(2)}/month</dd></>}
            <dt>Paid at checkout</dt><dd>€{Number(order.total).toFixed(2)}</dd>
            <dt>Order record</dt><dd><Badge value={order.status}/></dd>
            {subscription?.billingStartedAt&&<><dt>Billing started</dt><dd>{new Date(subscription.billingStartedAt).toLocaleDateString()}</dd></>}
            {subscription?.nextBillingAt&&<><dt>Next invoice</dt><dd>{new Date(subscription.nextBillingAt).toLocaleDateString()}</dd></>}
          </dl>
        </section>
        {(cancellable||subscriptionCancellable)&&<CancelPanel orderId={order.id} mode={cancellable?'order':'subscription'} onDone={load}/>}
      </aside>
    </div>

    <section className="panel">
      <h3>History</h3>
      {!events.length?<p className="muted">No lifecycle events yet.</p>:<ul className="history">{[...events].sort((a,b)=>b.occurredAt.localeCompare(a.occurredAt)).map(e=><li key={e.eventId}><time>{new Date(e.occurredAt).toLocaleString()}</time><span>{eventLabels[e.eventType]??e.eventType} <Badge value={e.status}/></span>{e.detail&&<small>{e.detail}</small>}</li>)}</ul>}
    </section>
  </article>
}

function CancelPanel({orderId,mode,onDone}:{orderId:string;mode:'order'|'subscription';onDone:()=>Promise<void>}){
  const[reason,setReason]=useState(''),[confirming,setConfirming]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const submit=async()=>{
    if(mode==='order'&&!reason.trim()){setError('Enter a reason for cancelling.');return}
    setBusy(true);setError('');
    try{if(mode==='order')await cancelOrder(orderId,reason);else await cancelSubscription(orderId);setConfirming(false);setReason('');await onDone()}
    catch(e){setError(e instanceof Error?e.message:'The cancellation failed.')}finally{setBusy(false)}
  };
  return <section className="panel panel--danger">
    <h3>{mode==='order'?'Cancel order':'Cancel subscription'}</h3>
    <p className="muted">{mode==='order'?'Available until the order completes. Stops the order and undoes completed steps: voids or refunds the payment, stops delivery and eSIM activation, and cancels billing.':'The order is complete. Cancelling stops recurring billing; the device and eSIM are not affected.'}</p>
    {mode==='order'&&<input aria-label="Cancellation reason" value={reason} maxLength={500} onChange={e=>{setReason(e.target.value);setError('')}} placeholder="Reason (required)"/>}
    {!confirming?<button className="danger" onClick={()=>setConfirming(true)}>{mode==='order'?'Cancel order…':'Cancel subscription…'}</button>
      :<div className="decision__buttons"><button className="danger danger--solid" disabled={busy} onClick={submit}>{busy?'Cancelling…':'Confirm cancellation'}</button><button className="ghost" disabled={busy} onClick={()=>setConfirming(false)}>Keep it</button></div>}
    {error&&<small className="error" role="alert">{error}</small>}
  </section>
}

/** Monthly-only billing: agents choose the day of the month (1-28); the next invoice is prorated up to the new day. */
function BillingDayControl({orderId,current,nextBillingAt,onSaved}:{orderId:string;current:number|null;nextBillingAt:string|null;onSaved:()=>Promise<void>}){
  const fallback=nextBillingAt?Math.min(new Date(nextBillingAt).getUTCDate(),28):1;
  const[day,setDay]=useState(current??fallback),[busy,setBusy]=useState(false),[error,setError]=useState(''),[saved,setSaved]=useState(false);
  const save=async()=>{setBusy(true);setError('');setSaved(false);try{await changeBillingDay(orderId,day);setSaved(true);await onSaved()}catch(e){setError(e instanceof Error?e.message:'Could not change the billing day.')}finally{setBusy(false)}};
  return <div className="billing-day">
    <label>Billing day (monthly)<select value={day} onChange={e=>{setDay(Number(e.target.value));setSaved(false)}}>{Array.from({length:28},(_,i)=>i+1).map(d=><option key={d} value={d}>{d}</option>)}</select></label>
    <button className="ghost" disabled={busy||day===(current??fallback)} onClick={save}>{busy?'Saving…':'Change billing day'}</button>
    <small className="muted">{nextBillingAt?`Next invoice ${new Date(nextBillingAt).toLocaleDateString()}. `:''}Changing the day prorates the next invoice.</small>
    {saved&&<small className="success" role="status">Billing day updated.</small>}
    {error&&<small className="error" role="alert">{error}</small>}
  </div>
}
