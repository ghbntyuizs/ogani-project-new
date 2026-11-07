import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi } from '../assets/api.js';

const current={accessToken:'old-access',refreshToken:'old-refresh',user:{id:3,email:'buyer@example.test',role:'CUSTOMER',fullName:'Test Buyer'}};
function setup(handler,options={}) {
  const values=new Map([['ogani.session',JSON.stringify(current)]]);
  const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  const calls=[];
  const api=createApi({base:'http://localhost:8080',storage,fetcher:async(url,request)=>{calls.push({url,...request});return handler(url,request);},locks:options.locks??null,createId:()=> 'owner-id'});
  return {api,calls,values};
}
const ok=data=>new Response(JSON.stringify({success:true,data}),{status:200,headers:{'Content-Type':'application/json'}});
const denied=()=>new Response(JSON.stringify({message:'Expired'}),{status:401});
test('public catalog never attaches a stored bearer token',async()=>{
  const env=setup(async()=>ok({content:[]}));await env.api.request('/api/products',{auth:false});assert.equal(env.calls[0].headers.get('Authorization'),null);
});
test('parallel expired requests rotate once and replay writes with original key/body',async()=>{
  let rotations=0;const env=setup(async(url,request)=>{
    if(url.endsWith('/api/auth/refresh')){rotations++;await new Promise(resolve=>setTimeout(resolve,5));return ok({...current,accessToken:'new-access',refreshToken:'new-refresh'});}
    return request.headers.get('Authorization')==='Bearer new-access'?ok({id:1}):denied();
  });
  const body={addressId:1},options={method:'POST',headers:{'Idempotency-Key':'same-key'},body};
  await Promise.all([env.api.request('/api/checkout',options),env.api.request('/api/cart')]);assert.equal(rotations,1);
  const writes=env.calls.filter(c=>c.url.endsWith('/api/checkout'));assert.equal(writes.length,2);
  assert.equal(writes[0].body,writes[1].body);assert.equal(writes[1].headers.get('Idempotency-Key'),'same-key');
});
test('expired refresh clears session and surfaces login requirement',async()=>{
  const env=setup(async()=>denied());await assert.rejects(env.api.request('/api/cart'));assert.equal(env.api.session(),null);
});
test('logout while refresh is in flight cannot resurrect a session',async()=>{
  let release,started;const ready=new Promise(resolve=>{started=resolve;});
  const env=setup(async(url)=>{
    if(url.endsWith('/api/auth/refresh')){started();return new Promise(resolve=>{release=()=>resolve(ok({...current,accessToken:'new-access',refreshToken:'new-refresh'}));});}
    if(url.endsWith('/api/auth/logout'))return ok(null);return denied();
  });
  const pending=env.api.request('/api/cart');await ready;await env.api.logout();release();await assert.rejects(pending);assert.equal(env.api.session(),null);
});
test('a response belonging to a logged-out customer cannot be accepted',async()=>{
  let release;const env=setup(async(url)=>url.endsWith('/api/auth/logout')?ok(null):new Promise(resolve=>{release=()=>resolve(ok({id:2}));}));
  const pending=env.api.request('/api/checkout',{method:'POST',body:{}});await new Promise(resolve=>setTimeout(resolve,0));await env.api.logout();release();await assert.rejects(pending);
});
test('malformed successful write response is uncertain instead of reporting success',async()=>{
  const env=setup(async()=>new Response('<html>proxy error</html>',{status:200}));await assert.rejects(env.api.request('/api/checkout',{method:'POST',body:{}}),error=>error.uncertain===true);
});
test('copied tab without ownership cannot reuse the refresh session',async()=>{
  const env=setup(async()=>ok(null),{locks:{request:async(_name,_options,fn)=>fn(null)}});
  await assert.rejects(env.api.request('/api/cart'));assert.equal(env.api.session(),null);assert.equal(env.calls.length,0);
});
test('customer client cannot send bearer credentials to an arbitrary origin',async()=>{
  const env=setup(async()=>ok(null));await assert.rejects(env.api.request('https://evil.test/api/cart'));assert.equal(env.calls.length,0);
});
test('unknown role login is rejected without storing its session',async()=>{
  const env=setup(async(url)=>url.endsWith('/api/auth/login')?ok({...current,user:{...current.user,role:'UNKNOWN'}}):ok(null));
  env.values.delete('ogani.session');await assert.rejects(env.api.login('admin@example.test','password'));assert.equal(env.api.session(),null);
});

test('null JSON write response retains uncertainty',async()=>{
  const env=setup(async()=>new Response('null',{status:200}));
  await assert.rejects(env.api.request('/api/checkout',{method:'POST',body:{}}),error=>error.uncertain===true);
});
test('refresh cannot switch customer identity',async()=>{
  const env=setup(async url=>url.endsWith('/api/auth/refresh')?ok({...current,user:{...current.user,id:99}}):url.endsWith('/api/auth/logout')?ok(null):denied());
  await assert.rejects(env.api.request('/api/cart'));assert.equal(env.api.session(),null);
});


test('rejected cached session becomes a stable login state instead of redirecting back as admin',async()=>{
  const env=setup(async()=>new Response(JSON.stringify({message:'Forbidden'}),{status:403}));
  assert.equal(await env.api.verifySession(),null);assert.equal(env.api.session(),null);
});
test('temporary auth check failure preserves session and requires retry, not an automatic redirect',async()=>{
  const env=setup(async()=>new Response(JSON.stringify({message:'Unavailable'}),{status:503}));
  await assert.rejects(env.api.verifySession(),error=>error.status===503);assert.equal(env.api.session().user.id,current.user.id);
});
test('authentication guard uses verified backend role rather than cached role',async()=>{
  const env=setup(async()=>ok({...current.user,role:'ADMIN'}));
  assert.equal((await env.api.verifySession()).role,'ADMIN');assert.equal(env.api.session().user.role,'ADMIN');
});
test('invalid current-user response is cleared without entering an auth redirect cycle',async()=>{
  const env=setup(async()=>ok({id:99,role:'ADMIN'}));assert.equal(await env.api.verifySession(),null);assert.equal(env.api.session(),null);
});

test('late 403 from an old auth check cannot erase a newly logged-in account',async()=>{
  let release;const env=setup(async url=>url.endsWith('/api/auth/login')?ok({...current,accessToken:'new-account-token',refreshToken:'new-r',user:{...current.user,id:4}}):new Promise(resolve=>release=()=>resolve(new Response(JSON.stringify({message:'Forbidden'}),{status:403}))));
  const oldCheck=env.api.verifySession();await new Promise(resolve=>setTimeout(resolve,0));await env.api.login('new','password');release();
  await assert.rejects(oldCheck,error=>error.sessionChanged===true);assert.equal(env.api.session().user.id,4);
});

test('a refreshed token that is denied by current-user check is cleared in the same verification',async()=>{
  const env=setup(async(url,init)=>url.endsWith('/api/auth/refresh')?ok({...current,accessToken:'rotated',refreshToken:'rotated-r'}):new Response(JSON.stringify({message:'Denied'}),{status:init.headers.get('Authorization')==='Bearer rotated'?403:401}));
  assert.equal(await env.api.verifySession(),null);assert.equal(env.api.session(),null);
});
