import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createApi} from '../assets/api.js';
import {landing,adminTransitions,canConfirmCod,orderQuery} from '../assets/roles.js';
const session=role=>({accessToken:'a',refreshToken:'r',user:{id:1,role}});
test('role landing distinguishes exact admin root from customer index',()=>{
  assert.equal(landing('CUSTOMER'),'index.html');assert.equal(landing('ADMIN'),'/');assert.equal(landing('STAFF'),'/');
});
test('same persisted session is accepted by both admin and storefront clients',async()=>{
  const values=new Map(),storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  const a=createApi({storage,locks:null,fetcher:async()=>Response.json({success:true,data:session('ADMIN')})});
  await a.login('admin','password');const b=createApi({storage,locks:null});assert.equal(b.session()?.user.role,'ADMIN');
});
test('staff upload sends FormData without JSON conversion or Content-Type boundary override',async()=>{
  const values=new Map(),storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};let sent;
  const api=createApi({storage,locks:null,fetcher:async(_url,init)=>{sent=init;return Response.json({success:true,data:session('STAFF')});}});
  await api.login('staff','password');const body=new FormData();body.append('file',new Blob(['x']), 'a.png');
  await api.request('/api/admin/uploads',{method:'POST',body});assert.equal(sent.body,body);assert.equal(sent.headers.get('Content-Type'),null);
});
test('only backend-supported status actions and COD collection are offered',()=>{
  assert.deepEqual(adminTransitions({orderStatus:'PENDING',payment:{method:'COD',status:'UNPAID'}}),['CONFIRMED','CANCELLED']);
  assert.deepEqual(adminTransitions({orderStatus:'SHIPPING',payment:{status:'UNPAID'}}),[]);
  assert.deepEqual(adminTransitions({orderStatus:'SHIPPING',payment:{status:'PAID'}}),['COMPLETED']);
  assert.deepEqual(adminTransitions({orderStatus:'CANCELLED',payment:{status:'UNPAID'}}),[]);
  assert.equal(canConfirmCod({orderStatus:'SHIPPING',payment:{method:'COD',status:'UNPAID'}}),true);
  assert.equal(canConfirmCod({orderStatus:'CONFIRMED',payment:{method:'COD',status:'UNPAID'}}),false);
});
test('order filters whitelist server fields, reset page and validate date range',()=>{
  const query=orderQuery(new URLSearchParams('keyword=Test&status=PENDING&paymentStatus=UNPAID&size=20&sort=createdAt%2Casc&evil=x'),2);
  assert.equal(query.get('page'),'2');assert.equal(query.get('evil'),null);assert.equal(query.get('paymentStatus'),'UNPAID');
  assert.throws(()=>orderQuery(new URLSearchParams('dateFrom=2026-11-07&dateTo=2026-11-01')));
});

test('paid cancellation, refunded fulfillment and unpaid online fulfillment stay unavailable',()=>{
  assert.deepEqual(adminTransitions({orderStatus:'PENDING',payment:{method:'COD',status:'PAID'}}),['CONFIRMED']);
  assert.deepEqual(adminTransitions({orderStatus:'CONFIRMED',payment:{method:'COD',status:'REFUNDED'}}),['CANCELLED']);
  assert.deepEqual(adminTransitions({orderStatus:'PENDING',payment:{method:'PAYPAL',status:'UNPAID'}}),['CANCELLED']);
});

test('a frozen old admin client cannot return its late response after another document logs in as customer',async()=>{
  const values=new Map(),storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};let release;
  const admin=createApi({storage,locks:null,fetcher:async url=>url.endsWith('/login')?Response.json({success:true,data:session('ADMIN')}):new Promise(resolve=>{release=()=>resolve(Response.json({success:true,data:{secret:'old admin data'}}));})});
  await admin.login('admin','password');const pending=admin.request('/api/admin/orders');await new Promise(resolve=>setTimeout(resolve,0));
  const customer=createApi({storage,locks:null,fetcher:async()=>Response.json({success:true,data:{...session('CUSTOMER'),user:{id:2,role:'CUSTOMER'}}})});
  await customer.login('customer','password');release();await assert.rejects(pending,error=>error.sessionChanged===true);assert.equal(admin.session().user.role,'CUSTOMER');
});
