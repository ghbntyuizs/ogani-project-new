import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createGuest} from '../assets/guest.js';
function memory(){const map=new Map();return {getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};}
function setup(handler){const calls=[],storage=memory(),pendingStorage=memory();const api={request:async(path,options)=>{calls.push({path,...structuredClone(options)});if(path==='/api/guest/session')return{scope:'a'.repeat(64)};return handler(path,options);}};return {guest:createGuest({api,storage,pendingStorage}),calls,storage,pendingStorage};}
test('anonymous basket uses public API and never reads saved addresses or account cart',async()=>{
  const env=setup((path,{body})=>({items:body.items.map(row=>({...row,available:true}))}));await env.guest.session();await env.guest.write('/api/cart/items',{method:'POST',body:{productId:1,quantity:2}});
  assert.deepEqual(env.guest.cart.lines(),[{productId:1,quantity:2}]);assert.deepEqual(env.calls.map(c=>c.path),['/api/guest/session','/api/guest/cart']);assert.ok(env.calls.every(c=>c.auth===false));
});
test('stock failure restores previous local quantity and unavailable item can still be removed',async()=>{
  const env=setup((path,{body})=>({items:body.items.map(row=>({...row,available:false}))}));env.guest.cart.set(1,1);
  await assert.rejects(env.guest.write('/api/cart/items/1',{method:'PUT',body:{quantity:3}}));assert.equal(env.guest.cart.lines()[0].quantity,1);
  await env.guest.write('/api/cart/items/1',{method:'DELETE'});assert.deepEqual(env.guest.cart.lines(),[]);
});
test('uncertain guest placement retries same body/key via guest endpoint and blocks basket edits',async()=>{
  let count=0;const env=setup(()=>{if(++count===1)throw Object.assign(new Error('timeout'),{status:0});return{id:42};});await env.guest.session();env.guest.cart.set(1,2);
  const body={cartToken:'b'.repeat(64),items:env.guest.cart.lines()};await assert.rejects(env.guest.checkout.place(body));
  await assert.rejects(env.guest.write('/api/cart/items/1',{method:'DELETE'}));await env.guest.checkout.retry();
  const posts=env.calls.filter(c=>c.path==='/api/guest/checkout');assert.deepEqual(posts[0],posts[1]);assert.equal(env.guest.checkout.pending(),null);assert.deepEqual(env.guest.cart.lines(),[]);
});
test('guest pending storage never overwrites customer checkout attempt',async()=>{
  const env=setup(()=>{throw new Error('offline');});await env.guest.session();env.pendingStorage.setItem('ogani.customer.checkout.3','untouched');
  await assert.rejects(env.guest.checkout.place({cartToken:'b'.repeat(64),items:[]}));assert.equal(env.pendingStorage.getItem('ogani.customer.checkout.3'),'untouched');
});
