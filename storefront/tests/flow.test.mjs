import { test } from 'node:test';
import assert from 'node:assert/strict';
import { safeReturnTo, checkoutBody, positiveId, orderCanCancel, serialQueue } from '../assets/flow.js';

test('return URLs stay on storefront allowlisted pages',()=>{
  assert.equal(safeReturnTo('checkout.html'),'checkout.html');
  for(const value of ['https://evil.test','//evil.test','javascript:alert(1)','../admin/index.html','login.html','unknown.html'])assert.equal(safeReturnTo(value),'index.html');
});
test('checkout sends server token and contact/address only, never client prices',()=>{
  const result=checkoutBody({cartToken:'a'.repeat(64),firstName:' Test ',lastName:' Buyer ',email:' buyer@example.test ',addressId:'2',orderNotes:'',total:0});
  assert.deepEqual(result,{cartToken:'a'.repeat(64),firstName:'Test',lastName:'Buyer',email:'buyer@example.test',paymentMethod:'COD',addressId:2,orderNotes:null});
});
test('invalid IDs cannot be coerced into another resource',()=>{
  for(const value of ['0','-1','1.5','1e3','oops','9007199254740993'])assert.throws(()=>positiveId(value));assert.equal(positiveId('42'),42);
});
test('only own pending unpaid COD displays customer cancellation action',()=>{
  assert.equal(orderCanCancel({orderStatus:'PENDING',paymentStatus:'UNPAID',paymentMethod:'COD'}),true);
  for(const order of [{orderStatus:'CONFIRMED',paymentStatus:'UNPAID',paymentMethod:'COD'},{orderStatus:'PENDING',paymentStatus:'PAID',paymentMethod:'COD'},{orderStatus:'PENDING',paymentStatus:'UNPAID',paymentMethod:'PAYPAL'}])assert.equal(orderCanCancel(order),false);
});
test('cart writes run in order and a failed write does not block later operations',async()=>{
  const events=[],queue=serialQueue(()=>1);let release;
  const first=queue(async()=>{events.push('first');await new Promise(resolve=>{release=resolve;});throw new Error('failed');});
  const failure=assert.rejects(first),second=queue(async()=>{events.push('second');return 2;});await Promise.resolve();
  assert.deepEqual(events,['first']);release();await failure;assert.equal(await second,2);assert.deepEqual(events,['first','second']);
});
test('queued cart action cannot execute under a changed login',async()=>{
  let owner=1,release;const queue=serialQueue(()=>owner);
  const first=queue(()=>new Promise(resolve=>{release=resolve;}));await Promise.resolve();
  let executed=false;const second=queue(()=>{executed=true;});const failure=assert.rejects(second);owner=2;release();await first;await failure;assert.equal(executed,false);
});
