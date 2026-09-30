import{useCallback,useEffect,useState,type FormEvent}from'react';
import{getBillingRuns,getBillingSchedule,getRunItems,previewBilling,saveBillingSchedule,searchInvoices,startBillingRun}from'../api/client';
import{Badge}from'../components/Badge';
import type{BillingPreview,BillingRun,BillingScheduleSettings,Invoice,RunItem}from'../types';

const euro=(value:number)=>`€${Number(value).toFixed(2)}`;
const todayIso=()=>new Date().toISOString().slice(0,10);
const when=(value?:string|null)=>value?new Date(value).toLocaleString():'—';
const orderLink=(orderId:string)=><a className="order-link" href={`#/orders/${orderId}`}>{orderId}</a>;

/** Monthly invoicing: plan a run (preview), run it now, manage the daily schedule, and review runs and invoices. */
export function InvoicingPage(){
  const[runs,setRuns]=useState<BillingRun[]|null>(null),[error,setError]=useState('');
  const loadRuns=useCallback(async()=>{try{setRuns(await getBillingRuns());setError('')}catch(e){setError(e instanceof Error?e.message:'Invoicing service is unavailable.')}},[]);
  useEffect(()=>{loadRuns();const id=setInterval(loadRuns,10000);return()=>clearInterval(id)},[loadRuns]);
  return <div className="invoicing">
    {error&&<div className="notice">{error}</div>}
    <div className="invoicing__top"><PlanAndRun onStarted={loadRuns}/><ScheduleSettings/></div>
    <RunHistory runs={runs}/>
    <InvoiceSearch/>
  </div>
}

function PlanAndRun({onStarted}:{onStarted:()=>Promise<void>}){
  const[date,setDate]=useState(todayIso()),[preview,setPreview]=useState<BillingPreview|null>(null),[busy,setBusy]=useState<'preview'|'run'|null>(null),[message,setMessage]=useState(''),[error,setError]=useState('');
  const future=date>todayIso();
  const runPreview=async()=>{setBusy('preview');setError('');setMessage('');try{setPreview(await previewBilling(date))}catch(e){setError(e instanceof Error?e.message:'Preview failed.')}finally{setBusy(null)}};
  const run=async()=>{setBusy('run');setError('');try{const started=await startBillingRun(date);setMessage(`Run #${started.runId} started for ${date}.`);setPreview(null);await onStarted()}catch(e){setError(e instanceof Error?e.message:'The run could not start.')}finally{setBusy(null)}};
  const toInvoice=preview?.lines.filter(line=>!line.existingInvoice)??[];
  return <section className="panel invoicing__plan" aria-label="Plan and run">
    <h3>Plan &amp; run invoicing</h3>
    <p className="muted">Preview who will be invoiced on a date (including future dates, to plan ahead), then run it. Runs are safe to repeat — nothing is invoiced or charged twice.</p>
    <div className="inline-form">
      <label>Run date<input type="date" value={date} onChange={e=>{setDate(e.target.value);setPreview(null);setMessage('')}}/></label>
      <button className="ghost" onClick={runPreview} disabled={busy!==null||!date}>{busy==='preview'?'Planning…':'Preview'}</button>
      <button onClick={run} disabled={busy!==null||!date||future} title={future?'Future dates can be previewed but not run':undefined}>{busy==='run'?'Starting…':'Run now'}</button>
    </div>
    {message&&<p className="success" role="status">{message}</p>}
    {error&&<small className="error" role="alert">{error}</small>}
    {preview&&<>
      <p className="preview-summary"><strong>{toInvoice.length}</strong> subscription{toInvoice.length===1?'':'s'} to invoice on {preview.runDate} · <strong>{euro(preview.total)}</strong> incl. VAT{preview.lines.length>toInvoice.length&&<> · {preview.lines.length-toInvoice.length} already invoiced</>}</p>
      {preview.lines.length>0&&<div className="table-wrap"><table>
        <thead><tr><th>Order</th><th>Customer</th><th>Plan</th><th>Period</th><th>Billing day</th><th>Net</th><th>VAT</th><th>Total</th><th></th></tr></thead>
        <tbody>{preview.lines.map(line=><tr key={line.subscriptionId}>
          <td>{orderLink(line.orderId)}</td><td>{line.customerId}</td><td>{line.planName}</td>
          <td>{line.periodStart} → {line.periodEnd}{line.prorated&&<small>prorated</small>}</td><td>{line.billingDay}</td>
          <td>{euro(line.netAmount)}</td><td>{euro(line.vatAmount)}</td><td><strong>{euro(line.totalAmount)}</strong></td>
          <td>{line.existingInvoice?<small>already {line.existingInvoice}</small>:<Badge value="TO_INVOICE"/>}</td>
        </tr>)}</tbody>
      </table></div>}
    </>}
  </section>
}

function ScheduleSettings(){
  const[settings,setSettings]=useState<BillingScheduleSettings|null>(null),[enabled,setEnabled]=useState(true),[time,setTime]=useState('02:00'),[busy,setBusy]=useState(false),[status,setStatus]=useState(''),[error,setError]=useState('');
  const apply=(s:BillingScheduleSettings)=>{setSettings(s);setEnabled(s.enabled);setTime(s.runTime.slice(0,5))};
  useEffect(()=>{getBillingSchedule().then(apply).catch(e=>setError(e instanceof Error?e.message:'Schedule unavailable.'))},[]);
  const save=async(event:FormEvent)=>{event.preventDefault();setBusy(true);setError('');setStatus('');try{apply(await saveBillingSchedule(enabled,time));setStatus('Schedule saved.')}catch(e){setError(e instanceof Error?e.message:'Save failed.')}finally{setBusy(false)}};
  return <section className="panel" aria-label="Daily schedule">
    <h3>Daily schedule</h3>
    {!settings?<p className="muted">{error||'Loading…'}</p>:<form onSubmit={save} className="schedule-form">
      <label className="checkbox"><input type="checkbox" checked={enabled} onChange={e=>setEnabled(e.target.checked)}/> Run automatically every day</label>
      <label>Run time ({settings.zone})<input type="time" value={time} onChange={e=>setTime(e.target.value)} required/></label>
      <p className="muted">{settings.enabled&&settings.nextRunAt?<>Next run: {when(settings.nextRunAt)}</>:'Paused — runs only when started manually.'}</p>
      <button disabled={busy}>{busy?'Saving…':'Save schedule'}</button>
      {status&&<small className="success" role="status">{status}</small>}
      {error&&<small className="error" role="alert">{error}</small>}
    </form>}
  </section>
}

function RunHistory({runs}:{runs:BillingRun[]|null}){
  const[open,setOpen]=useState<number|null>(null),[items,setItems]=useState<RunItem[]|null>(null);
  const toggle=async(runId:number)=>{if(open===runId){setOpen(null);return}setOpen(runId);setItems(null);setItems(await getRunItems(runId).catch(()=>[]))};
  return <section className="panel" aria-label="Run history">
    <h3>Run history</h3>
    {!runs?<p className="muted">Loading…</p>:!runs.length?<p className="muted">No runs yet.</p>:<div className="table-wrap"><table>
      <thead><tr><th>Run</th><th>Run date</th><th>Trigger</th><th>Status</th><th>Started</th><th>Customers</th><th>Invoiced</th><th>Failed</th><th></th></tr></thead>
      <tbody>{runs.map(run=><tr key={run.runId}>
        <td>#{run.runId}</td><td>{run.runDate??'—'}</td><td>{run.trigger?.toLowerCase()??'—'}</td>
        <td><Badge value={run.status}/>{run.exitDescription&&<small className="reason">{run.exitDescription.slice(0,140)}</small>}</td>
        <td>{when(run.startedAt)}</td><td>{run.counts?.total??0}</td><td>{run.counts?.invoiced??0}</td><td>{run.counts?.failed?<strong className="error">{run.counts.failed}</strong>:0}</td>
        <td><button className="ghost" onClick={()=>toggle(run.runId)} aria-expanded={open===run.runId}>{open===run.runId?'Hide':'Details'}</button></td>
      </tr>)}</tbody>
    </table></div>}
    {open!==null&&<div className="run-items"><h4>Run #{open}: every customer picked up</h4>{!items?<p className="muted">Loading…</p>:!items.length?<p className="muted">No subscriptions were due.</p>:<div className="table-wrap"><table>
      <thead><tr><th>Order</th><th>Customer</th><th>Period from</th><th>Outcome</th><th>Invoice</th><th>Detail</th></tr></thead>
      <tbody>{items.map(item=><tr key={item.id}><td>{orderLink(item.orderId)}</td><td>{item.customerId}</td><td>{item.periodStart}</td><td><Badge value={item.state}/></td><td>{item.invoiceNumber??'—'}</td><td className="wrap">{item.message??''}</td></tr>)}</tbody>
    </table></div>}</div>}
  </section>
}

function InvoiceSearch(){
  const[customerId,setCustomerId]=useState(''),[invoices,setInvoices]=useState<Invoice[]|null>(null),[error,setError]=useState('');
  const search=async(event:FormEvent)=>{event.preventDefault();if(!customerId.trim()){setError('Enter a customer ID.');return}setError('');try{setInvoices(await searchInvoices(customerId))}catch(e){setError(e instanceof Error?e.message:'Search failed.')}};
  return <section className="panel" aria-label="Invoices by customer">
    <h3>Invoices by customer</h3>
    <form className="inline-form" onSubmit={search}><input aria-label="Invoice customer ID" value={customerId} onChange={e=>setCustomerId(e.target.value)} placeholder="Customer ID"/><button>Search</button></form>
    {error&&<small className="error" role="alert">{error}</small>}
    {invoices&&(!invoices.length?<p className="muted">No invoices for this customer.</p>:<div className="table-wrap"><table>
      <thead><tr><th>Invoice</th><th>Order</th><th>Period</th><th>Net</th><th>VAT</th><th>Total</th><th>Status</th><th>Issued</th></tr></thead>
      <tbody>{invoices.map(invoice=><tr key={invoice.invoiceNumber}>
        <td><strong>{invoice.invoiceNumber}</strong><small>run #{invoice.runId}</small></td><td>{orderLink(invoice.orderId)}</td>
        <td>{invoice.periodStart} → {invoice.periodEnd}{invoice.prorated&&<small>prorated</small>}</td>
        <td>{euro(invoice.netAmount)}</td><td>{euro(invoice.vatAmount)}</td><td><strong>{euro(invoice.totalAmount)}</strong></td>
        <td><Badge value={invoice.status}/>{invoice.failureReason&&<small className="reason">{invoice.failureReason}</small>}</td><td>{when(invoice.issuedAt)}</td>
      </tr>)}</tbody>
    </table></div>)}
  </section>
}
