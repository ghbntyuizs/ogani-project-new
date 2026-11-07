import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createCheckoutState } from '../assets/checkout-state.js';

function setup(handler) {
  const values=new Map(), calls=[];let sequence=0,userId=3;
  const storage={ getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k) };
  const state=createCheckoutState({storage,userId:()=>userId,createKey:()=>`key-${++sequence}`,request:async(path,options)=>{calls.push({path,...structuredClone(options)});return handler?.(path,options);}});
  return {state,values,calls,storage,switchUser:id=>{userId=id;}};
}
const body={cartToken:'a'.repeat(64),firstName:'Test',lastName:'Buyer',email:'buyer@example.test',paymentMethod:'COD',addressId:1,orderNotes:null};
test('late shared-scope replay cannot clear a newer pending attempt',async()=>{
  const values=new Map(),storage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  let finish;const create=(request,key)=>createCheckoutState({storage,userId:()=>3,prefix:'guest.',request,createKey:()=>key});
  const slow=create(()=>new Promise(resolve=>{finish=resolve;}),'old');const first=slow.place(body);
  await create(async()=>({id:1}),'unused').retry();
  await assert.rejects(create(async()=>{throw new Error('timeout');},'new').place({...body,cartToken:'b'.repeat(64)}));
  finish({id:1});await first;assert.equal(JSON.parse(storage.getItem('guest.3')).key,'new');
});
test('one logical request uses the same persisted body and key after a lost response',async()=>{
  let attempts=0;const env=setup(()=>{if(++attempts===1)throw new Error('response lost');return{id:14,orderCode:'COD-14'};});
  await assert.rejects(env.state.place(body));assert.ok(env.state.pending());
  await env.state.retry();assert.equal(env.calls.length,2);assert.deepEqual(env.calls[0],env.calls[1]);assert.equal(env.state.pending(),null);
});
test('pending attempt resumes after reload without any cart request',async()=>{
  const env=setup(()=>{throw Object.assign(new Error('timeout'),{status:0});});await assert.rejects(env.state.place(body));
  const calls=[];const restored=createCheckoutState({storage:env.storage,userId:()=>3,createKey:()=>{throw new Error('must not create a new key');},request:async(path,options)=>{calls.push({path,options});return{id:15};}});
  await restored.retry();assert.deepEqual(calls.map(c=>c.path),['/api/checkout']);assert.equal(calls[0].options.headers['Idempotency-Key'],'key-1');
});
test('uncertain placement cannot be silently replaced with edited data',async()=>{
  const env=setup(()=>{throw new Error('offline');});await assert.rejects(env.state.place(body));
  await assert.rejects(env.state.place({...body,addressId:2}));assert.equal(env.calls.length,1);
});
test('server conflict clears attempt so reviewed new body gets a new key',async()=>{
  let attempts=0;const env=setup(()=>{if(++attempts===1)throw Object.assign(new Error('cart changed'),{status:409});return{id:16};});
  await assert.rejects(env.state.place(body));assert.equal(env.state.pending(),null);
  await env.state.place({...body,cartToken:'b'.repeat(64)});assert.equal(env.calls[1].headers['Idempotency-Key'],'key-2');
});
test('auth expiry preserves pending request, while another customer cannot reuse it',async()=>{
  const env=setup(()=>{throw Object.assign(new Error('login required'),{status:401});});await assert.rejects(env.state.place(body));
  assert.ok(env.state.pending());env.switchUser(4);assert.equal(env.state.pending(),null);await assert.rejects(env.state.retry());assert.equal(env.calls.length,1);
  env.switchUser(3);assert.ok(env.state.pending());env.state.clear();assert.equal(env.state.pending(),null);
});
test('invalid successful response remains uncertain and can be retried',async()=>{
  let attempts=0;const env=setup(()=>++attempts===1?{}:{id:17});await assert.rejects(env.state.place(body));assert.ok(env.state.pending());await env.state.retry();
});
test('parallel button clicks do not create two HTTP requests',async()=>{
  let release;const env=setup(()=>new Promise(resolve=>{release=resolve;}));const first=env.state.place(body);
  await assert.rejects(env.state.place(body));assert.equal(env.calls.length,1);release({id:18});await first;
});
