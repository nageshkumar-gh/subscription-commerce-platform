import{useState,type FormEvent}from'react';
import{getActiveSubscriptions}from'../api/client';
import{Badge}from'../components/Badge';
import type{Order,Subscription}from'../types';
import{describeItem}from'./queues';

/** Billing lookup: every ACTIVE subscription for one customer. */
export function CustomerSubscriptions({orders}:{orders:Order[]}){
  const[customerId,setCustomerId]=useState(''),[searched,setSearched]=useState(''),[results,setResults]=useState<Subscription[]|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const search=async(event:FormEvent)=>{
    event.preventDefault();
    const id=customerId.trim();
    if(!id){setError('Enter a customer ID.');return}
    setBusy(true);setError('');
    try{setResults(await getActiveSubscriptions(id));setSearched(id)}catch(e){setError(e instanceof Error?e.message:'Search failed.');setResults(null)}finally{setBusy(false)}
  };
  const byId=new Map(orders.map(order=>[order.id,order]));
  const monthly=results?.reduce((sum,s)=>sum+Number(s.monthlyAmount),0)??0;
  return <section className="panel customer-search" aria-label="Active subscriptions by customer">
    <h3>Active subscriptions by customer</h3>
    <form className="customer-search__form" onSubmit={search}>
      <input aria-label="Customer ID" value={customerId} onChange={e=>{setCustomerId(e.target.value);setError('')}} placeholder="Customer ID"/>
      <button disabled={busy}>{busy?'Searching…':'Search'}</button>
    </form>
    {error&&<small className="error" role="alert">{error}</small>}
    {results&&(!results.length?<p className="muted">No active subscriptions for customer {searched}.</p>:<>
      <p className="muted">{results.length} active subscription{results.length===1?'':'s'} for customer {searched} · €{monthly.toFixed(2)}/month in total</p>
      <div className="table-wrap"><table>
        <thead><tr><th>Order</th><th>Item</th><th>Status</th><th>Monthly</th><th>Billing started</th><th>Next invoice</th></tr></thead>
        <tbody>{results.map(s=><tr key={s.id}>
          <td><a className="order-link" href={`#/orders/${s.orderId}`}><strong>{s.orderId}</strong></a></td>
          <td>{describeItem(byId.get(s.orderId),s.planName)}</td>
          <td><Badge value={s.status}/></td>
          <td>€{Number(s.monthlyAmount).toFixed(2)}</td>
          <td>{s.billingStartedAt?new Date(s.billingStartedAt).toLocaleDateString():'—'}</td>
          <td>{s.nextBillingAt?new Date(s.nextBillingAt).toLocaleDateString():'—'}</td>
        </tr>)}</tbody>
      </table></div>
    </>)}
  </section>
}
