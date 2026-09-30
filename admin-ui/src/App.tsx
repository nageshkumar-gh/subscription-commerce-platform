import{useCallback,useEffect,useMemo,useState}from'react';
import{decide,getOperations,getServiceHealth,getTracking}from'./api/client';
import{Badge}from'./components/Badge';
import{ApprovalQueue}from'./pages/ApprovalQueue';
import{CustomerSubscriptions}from'./pages/CustomerSubscriptions';
import{InvoicingPage}from'./pages/InvoicingPage';
import{OrderPage}from'./pages/OrderPage';
import{buildQueues,needsAction,type QueueRow}from'./pages/queues';
import type{Decision,Operations,OrderTracking,ServiceHealth,Step}from'./types';
import'./styles.css';

/** Case-insensitive match of every search word against any of the row's fields. */
const matches=(query:string,fields:(string|null|undefined)[])=>{const text=fields.filter(Boolean).join(' ').toLowerCase().replaceAll('_',' ');return query.toLowerCase().split(/\s+/).filter(Boolean).every(word=>text.includes(word))};
const byDate=(a:string|null|undefined,b:string|null|undefined,newestFirst:boolean)=>(newestFirst?-1:1)*(a??'').localeCompare(b??'');
const emptyOperations:Operations={orders:[],payments:[],activations:[],fulfillments:[],subscriptions:[]};
type Tab='orders'|Step|'invoicing';
/** The single-order page lives at #/orders/<id> so it can be linked, bookmarked and left with the Back button. */
const orderFromHash=()=>decodeURIComponent(/^#\/orders\/(.+)$/.exec(window.location.hash)?.[1]??'')||null;

export function App(){
  const[tab,setTab]=useState<Tab>('orders'),[openOrder,setOpenOrder]=useState<string|null>(orderFromHash);
  useEffect(()=>{const sync=()=>{setOpenOrder(orderFromHash());window.scrollTo(0,0)};window.addEventListener('hashchange',sync);return()=>window.removeEventListener('hashchange',sync)},[]);
  const showTab=(next:Tab)=>{setTab(next);if(openOrder)window.location.hash='#/'};
  const[orders,setOrders]=useState<OrderTracking[]>([]),[health,setHealth]=useState<ServiceHealth[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState(''),[query,setQuery]=useState('');
  const[operations,setOperations]=useState<Operations>(emptyOperations),[unavailable,setUnavailable]=useState<string[]>([]);
  const[refreshing,setRefreshing]=useState(false),[updatedAt,setUpdatedAt]=useState<Date|null>(null),[newestFirst,setNewestFirst]=useState(true);
  const load=useCallback(async()=>{
    setError('');setRefreshing(true);
    const[trackingResult,healthResult,operationsResult]=await Promise.allSettled([getTracking(),getServiceHealth(),getOperations()]);
    if(trackingResult.status==='fulfilled')setOrders(trackingResult.value);else setError('Order tracking is temporarily unavailable.');
    if(healthResult.status==='fulfilled')setHealth(healthResult.value);
    if(operationsResult.status==='fulfilled'){setOperations(operationsResult.value.data);setUnavailable(operationsResult.value.unavailable)}
    setLoading(false);setRefreshing(false);setUpdatedAt(new Date());
  },[]);
  useEffect(()=>{load();const id=setInterval(load,10000);return()=>clearInterval(id)},[load]);
  const queues=useMemo(()=>buildQueues(operations),[operations]);
  const onDecide=useCallback(async(step:Step,row:QueueRow,decision:Decision,reason:string)=>{await decide(step,row.key,decision,reason);await load()},[load]);

  const activeQueue=queues.find(queue=>queue.step===tab);
  const visible=orders.filter(o=>matches(query,[o.id,o.customerId,o.productName,o.storage,o.planName,o.status,o.workflow,o.payment,o.activation,o.fulfillment,o.billing])).sort((a,b)=>byDate(a.createdAt,b.createdAt,newestFirst));
  const sortHeader=<th aria-sort={newestFirst?'descending':'ascending'}><button className="sort" onClick={()=>setNewestFirst(v=>!v)} title="Sort by order date">Order {newestFirst?'↓':'↑'}</button></th>;
  return <>
    <header><div><p>OPERATIONS CONSOLE</p><h1>{openOrder?'Order details':tab==='invoicing'?'Monthly invoicing':activeQueue?.title??'Order tracking'}</h1></div><span className="role">Agent view</span></header>
    <nav className="tabs" aria-label="Sections">
      <button aria-current={!openOrder&&tab==='orders'?'page':undefined} onClick={()=>showTab('orders')}>Orders</button>
      {queues.map(queue=>{const count=queue.rows.filter(needsAction).length;return <button key={queue.step} aria-current={!openOrder&&tab===queue.step?'page':undefined} onClick={()=>showTab(queue.step)}>{queue.tab}{count>0&&<span className="count" aria-label={`${count} need action`}>{count}</span>}</button>})}
      <button aria-current={!openOrder&&tab==='invoicing'?'page':undefined} onClick={()=>showTab('invoicing')}>Invoicing</button>
    </nav>
    <main>
      <section className="health" aria-label="Service health">{health.map(service=><span key={service.name} className={`health__item health__item--${service.status.toLowerCase()}`}><i/>{service.name}</span>)}</section>
      {openOrder?<OrderPage key={openOrder} orderId={openOrder}/>:tab==='invoicing'?<InvoicingPage/>:<>
      <section className="toolbar"><label>Search<input type="search" value={query} onChange={e=>setQuery(e.target.value)} placeholder="Order, customer, product, plan, or status"/></label><div className="refresh">{updatedAt&&<small aria-live="polite">Updated {updatedAt.toLocaleTimeString()}</small>}<button onClick={load} disabled={refreshing}>{refreshing?'Refreshing…':'Refresh'}</button></div></section>
      {activeQueue?<>
        {activeQueue.step==='billing'&&<CustomerSubscriptions orders={operations.orders}/>}
        {unavailable.length>0&&<div className="notice">Could not load: {unavailable.join(', ')}. Showing what is available.</div>}
        {loading?<div className="state">Loading…</div>:<ApprovalQueue key={activeQueue.step} queue={{...activeQueue,rows:activeQueue.rows.filter(r=>matches(query,[r.orderId,r.customerId,r.item,r.reference,r.status,r.statusReason])).sort((a,b)=>byDate(a.updatedAt,b.updatedAt,newestFirst))}} sortHeader={sortHeader} onDecide={onDecide}/>}
      </>:<>
        {loading?<div className="state">Loading distributed status…</div>:error?<div className="state error">{error}</div>:!visible.length?<div className="state">No orders found.</div>:<div className="table-wrap"><table><thead><tr>{sortHeader}<th>Customer</th><th>Product</th><th>Order</th><th>Workflow</th><th>Payment</th><th>Delivery</th><th>eSIM</th><th>Billing</th></tr></thead><tbody>{visible.map(o=><tr key={o.id}><td><a className="order-link" href={`#/orders/${o.id}`}><strong>{o.id}</strong></a><small>{new Date(o.createdAt).toLocaleString()}</small></td><td>{o.customerId}</td><td>{o.productName}<small>{o.storage} · {o.planName}</small></td><td><Badge value={o.status}/></td><td><Badge value={o.workflow}/></td><td><Badge value={o.payment}/></td><td><Badge value={o.fulfillment}/></td><td><Badge value={o.activation}/></td><td><Badge value={o.billing}/></td></tr>)}</tbody></table></div>}
      </>}
      </>}
    </main>
  </>
}
