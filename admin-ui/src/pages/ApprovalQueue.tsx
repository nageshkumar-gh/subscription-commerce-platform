import{useState,type ReactNode}from'react';
import{Badge}from'../components/Badge';
import type{Decision,Step}from'../types';
import{needsAction,type Queue,type QueueRow}from'./queues';

type Props={queue:Queue;sortHeader:ReactNode;onDecide:(step:Step,row:QueueRow,decision:Decision,reason:string)=>Promise<void>};

export function ApprovalQueue({queue,sortHeader,onDecide}:Props){
  const[showAll,setShowAll]=useState(false);
  const pending=queue.rows.filter(needsAction);
  const rows=showAll?queue.rows:pending;
  return <section aria-label={queue.title}>
    <div className="queue-head">
      <div><h2>{queue.title}</h2><p>{queue.description}</p></div>
      <div className="segmented" role="group" aria-label="Filter">
        <button aria-pressed={!showAll} onClick={()=>setShowAll(false)}>Needs action ({pending.length})</button>
        <button aria-pressed={showAll} onClick={()=>setShowAll(true)}>All ({queue.rows.length})</button>
      </div>
    </div>
    {!rows.length?<div className="state">{showAll?'Nothing here yet.':'Nothing is waiting for a decision.'}</div>:
    <div className="table-wrap"><table>
      <thead><tr>{sortHeader}<th>Item</th><th>Status</th>{queue.step==='billing'&&<th>Prerequisites</th>}<th>Decision</th></tr></thead>
      <tbody>{rows.map(row=><tr key={row.key}>
        <td><a className="order-link" href={`#/orders/${row.orderId}`}><strong>{row.orderId}</strong></a><small>Customer {row.customerId}</small>{row.updatedAt&&<small>{new Date(row.updatedAt).toLocaleString()}</small>}</td>
        <td>{row.item}<small>{row.reference}</small></td>
        <td><Badge value={row.status}/>{row.statusReason&&<small className="reason">“{row.statusReason}”</small>}</td>
        {queue.step==='billing'&&<td><ul className="checks">{row.checks?.map(check=><li key={check.label} className={check.ok?'ok':'waiting'}>{check.ok?'✓':'…'} {check.label} <span>{check.status.replaceAll('_',' ').toLowerCase()}</span></li>)}</ul></td>}
        <td><DecisionControls step={queue.step} row={row} onDecide={onDecide}/></td>
      </tr>)}</tbody>
    </table></div>}
  </section>
}

export function DecisionControls({step,row,onDecide}:{step:Step;row:QueueRow;onDecide:Props['onDecide']}){
  const[reason,setReason]=useState(''),[busy,setBusy]=useState<Decision|null>(null),[error,setError]=useState('');
  if(!row.approveLabel&&!row.canReject)return <span className="muted">No action needed</span>;
  const submit=async(decision:Decision)=>{
    if(decision==='reject'&&!reason.trim()){setError('Enter a reason to reject.');return}
    setBusy(decision);setError('');
    try{await onDecide(step,row,decision,reason);setReason('')}catch(e){setError(e instanceof Error?e.message:'The decision could not be saved.')}finally{setBusy(null)}
  };
  return <div className="decision">
    <input aria-label={`Reason for order ${row.orderId}`} value={reason} maxLength={500} onChange={e=>{setReason(e.target.value);setError('')}} placeholder="Reason (required to reject)"/>
    <div className="decision__buttons">
      {row.approveLabel&&<button onClick={()=>submit('approve')} disabled={busy!==null||!!row.blocked} title={row.blocked}>{busy==='approve'?'Saving…':row.approveLabel}</button>}
      {row.canReject&&<button className="danger" onClick={()=>submit('reject')} disabled={busy!==null}>{busy==='reject'?'Saving…':'Reject'}</button>}
    </div>
    {row.blocked&&<small className="muted">{row.blocked}</small>}
    {error&&<small className="error" role="alert">{error}</small>}
  </div>
}
