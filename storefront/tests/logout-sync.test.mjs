import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createApi} from '../assets/api.js';
function tabs(){
  const stamps=new Map(),listeners=new Set();
  const bus={stamp:id=>stamps.get(id)||'',publish(id){stamps.set(id,String(Number(stamps.get(id)||0)+1));for(const fn of listeners)fn(id);},subscribe(fn){listeners.add(fn);return()=>listeners.delete(fn);}};
  const make=(id=1)=>{const data=new Map(),storage={getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};let revocations=0;
    const api=createApi({storage,locks:null,logoutBus:bus,fetcher:async path=>{if(path.endsWith('/logout'))revocations++;return Response.json({success:true,data:path.endsWith('/login')?{accessToken:'a',refreshToken:'r-'+id,user:{id,role:'ADMIN'}}:null});}});
    return {api,data,revocations:()=>revocations};};return {make,bus,stamps,notify:id=>{for(const fn of listeners)fn(id);}};
}
test('logout in admin clears the same account in a separate storefront tab and notifies the UI',async()=>{
  const {make}=tabs(),admin=make(),store=make();await admin.api.login('a','p');await store.api.login('a','p');let reason;
  store.api.subscribe(value=>reason=value);await admin.api.logout();assert.equal(store.api.session(),null);assert.equal(reason,'logout');assert.equal(store.revocations(),1);
});
test('logout does not clear a different customer tab',async()=>{
  const {make}=tabs(),admin=make(1),other=make(2);await admin.api.login('a','p');await other.api.login('b','p');await admin.api.logout();assert.equal(other.api.session().user.id,2);
});
test('cached tab checks durable logout stamp even if the storage event was missed',async()=>{
  const {make,stamps}=tabs(),store=make();await store.api.login('a','p');stamps.set(1,'missed-logout');assert.equal(store.api.session(),null);
});
test('new deliberate login after logout survives an old delayed notification',async()=>{
  const {make,notify}=tabs(),store=make();await store.api.login('a','p');await store.api.logout();await store.api.login('a','p');
  // Re-notifying with the existing durable stamp must not erase a new session.
  notify(1);assert.equal(store.api.session().user.id,1);
});


test('logout received during refresh cannot resurrect the storefront session',async()=>{
  const {make,bus}=tabs(),admin=make();await admin.api.login('a','p');let release,started;const ready=new Promise(resolve=>started=resolve);
  const values=new Map(),storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  const value={accessToken:'a',refreshToken:'r',user:{id:1,role:'ADMIN'}};
  const store=createApi({storage,locks:null,logoutBus:bus,fetcher:async path=>{
    if(path.endsWith('/login'))return Response.json({success:true,data:value});
    if(path.endsWith('/refresh')){started();return new Promise(resolve=>release=()=>resolve(Response.json({success:true,data:{...value,accessToken:'new',refreshToken:'new-r'}})));}
    if(path.endsWith('/logout'))return Response.json({success:true,data:null});
    return Response.json({message:'Expired'},{status:401});
  }});
  await store.login('a','p');const request=store.request('/api/auth/me');await ready;await admin.api.logout();release();await assert.rejects(request);assert.equal(store.session(),null);
});
test('logout transport shares only account markers through real storage events',async()=>{
  const {createLogoutBus}=await import('../assets/logout-sync.js');const values=new Map(),events=[null,null];
  function bus(index){return createLogoutBus({nonce:()=> 'marker-only',events:{addEventListener:(_name,fn)=>events[index]=fn},storage:{getItem:k=>values.get(k),setItem(k,v){values.set(k,v);events[1-index]?.({key:k,newValue:v});}}});}
  const a=bus(0),b=bus(1);let user;b.subscribe(id=>user=id);a.publish(3);assert.equal(user,3);assert.equal(b.stamp(3),'marker-only');assert.deepEqual([...values.keys()],['ogani.logout.3']);
});
