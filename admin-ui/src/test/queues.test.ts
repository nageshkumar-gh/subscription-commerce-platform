import{describe,expect,it}from'vitest';
import{buildQueues,needsAction}from'../pages/queues';
import type{Operations}from'../types';

const base:Operations={
  orders:[{id:'order-1',customerId:'customer-1',productName:'Phone',storage:'1 TB',planName:'Unlimited',total:10,status:'PENDING_PAYMENT',createdAt:'2026-01-01T00:00:00Z'}],
  payments:[{id:'pay-1',orderId:'order-1',customerId:'customer-1',amount:10,currency:'EUR',transactionReference:'PAY-1',status:'COMPLETED',createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-01T00:00:00Z'}],
  activations:[{id:'act-1',orderId:'order-1',customerId:'customer-1',planId:'plan-1',iccid:'8944',status:'ACTIVE',requestedAt:'2026-01-01T00:00:00Z'}],
  fulfillments:[{id:'ful-1',orderId:'order-1',customerId:'customer-1',productId:'product-1',trackingNumber:'TRACK-1',status:'DISPATCHED',createdAt:'2026-01-01T00:00:00Z'}],
  subscriptions:[{id:'sub-1',orderId:'order-1',customerId:'customer-1',planName:'Unlimited',monthlyAmount:29.99,status:'READY_FOR_BILLING',createdAt:'2026-01-01T00:00:00Z'}],
};
const queue=(data:Operations,step:string)=>buildQueues(data).find(q=>q.step===step)!;

describe('approval queues',()=>{
  it('offers the next delivery step and allows rejection until delivered',()=>{
    const[row]=queue(base,'fulfillment').rows;
    expect(row.approveLabel).toBe('Mark delivered');expect(row.canReject).toBe(true);expect(row.item).toBe('Phone · 1 TB · Unlimited');
    const[delivered]=queue({...base,fulfillments:[{...base.fulfillments[0],status:'DELIVERED'}]},'fulfillment').rows;
    expect(delivered.approveLabel).toBeNull();expect(delivered.canReject).toBe(false);expect(needsAction(delivered)).toBe(false);
  });
  it('blocks billing until every prior step is complete',()=>{
    const[row]=queue(base,'billing').rows;
    expect(row.blocked).toBe('Waiting for delivery');expect(needsAction(row)).toBe(false);
    expect(row.checks?.map(c=>c.label)).toEqual(['Payment','Delivery','eSIM']);expect(row.checks?.map(c=>c.ok)).toEqual([true,false,true]);
  });
  it('allows billing once payment, activation and delivery are done',()=>{
    const[row]=queue({...base,fulfillments:[{...base.fulfillments[0],status:'DELIVERED'}]},'billing').rows;
    expect(row.blocked).toBeUndefined();expect(row.approveLabel).toBe('Start billing');expect(needsAction(row)).toBe(true);
  });
  it('only lets pending payments be decided',()=>{
    expect(queue(base,'payment').rows[0].approveLabel).toBeNull();
    const[pending]=queue({...base,payments:[{...base.payments[0],status:'PENDING'}]},'payment').rows;
    expect(pending.approveLabel).toBe('Approve payment');expect(pending.canReject).toBe(true);
  });
});
