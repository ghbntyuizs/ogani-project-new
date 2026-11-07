import { uuid } from './flow.js';
import {createLogoutBus} from './logout-sync.js';

export class ApiError extends Error {
  constructor(message,status=0,options={}) {super(message);this.status=status;Object.assign(this,options);}
}
export function createApi({base='',storage=globalThis.sessionStorage,fetcher=globalThis.fetch,locks=globalThis.navigator?.locks,createId=()=>uuid(),logoutBus=createLogoutBus()}={}) {
  base=base.replace(/\/$/,'');const key='ogani.session',ownerKey=key+'.owner';
  let epoch=0,refreshing=null,ownership=null,releaseOwnership=null;const listeners=new Set();
  function session() {
    try {const value=JSON.parse(storage.getItem(key)||'null');if(!validSession(value))return null;
      if(logoutBus&&(value._logoutStamp||'')!==logoutBus.stamp(value.user.id)){invalidate(value);return null;}return value;}
    catch {storage.removeItem(key);return null;}
  }
  function store(value,reason='refresh') {
    if(reason!=='refresh')epoch++;
    if(value&&reason==='login'){releaseOwnership?.();releaseOwnership=null;ownership=null;storage.removeItem(ownerKey);}
    if(value){value={...value,_logoutStamp:reason==='login'?(logoutBus?.stamp(value.user.id)||''):(value._logoutStamp??JSON.parse(storage.getItem(key)||'null')?._logoutStamp??'')};storage.setItem(key,JSON.stringify(value));if(!storage.getItem(ownerKey))storage.setItem(ownerKey,createId());}
    else {storage.removeItem(key);storage.removeItem(ownerKey);releaseOwnership?.();releaseOwnership=null;ownership=null;}
    for(const listener of listeners)listener(reason);
  }
  async function ensureOwnership() {
    if(!session()||!locks)return;
    const acquiringEpoch=epoch;
    if(!ownership) {
      if(!storage.getItem(ownerKey))storage.setItem(ownerKey,createId());
      ownership=new Promise((resolve,reject)=>{
        locks.request('ogani-customer-'+storage.getItem(ownerKey),{ifAvailable:true},lock=>{
          if(!lock||epoch!==acquiringEpoch){resolve(false);return;}resolve(true);return new Promise(release=>{releaseOwnership=release;});
        }).catch(reject);
      });
    }
    const allowed=await ownership;
    if(epoch!==acquiringEpoch)throw changed();
    if(!allowed){store(null,'expired');throw new ApiError('Phiên này đang mở trong tab khác. Vui lòng đăng nhập riêng cho tab này.',401);}
  }
  async function send(path,options={},token) {
    if(typeof path!=='string'||!path.startsWith('/api/')||path.includes('://')||path.includes('\\'))throw new ApiError('Đường dẫn API không hợp lệ.');
    const {auth:_auth,...init}=options,headers=new Headers(options.headers);if(token)headers.set('Authorization','Bearer '+token);
    const multipart=typeof FormData!=='undefined'&&options.body instanceof FormData;
    const body=options.body==null?undefined:multipart?options.body:JSON.stringify(options.body);if(body!==undefined&&!multipart)headers.set('Content-Type','application/json');
    let response;
    try {response=await fetcher(base+path,{...init,headers,body,signal:options.signal||(globalThis.AbortSignal?.timeout?.(15000))});}
    catch(error){throw new ApiError('Chưa kết nối được tới cửa hàng. Vui lòng thử lại.',0,{uncertain:true,aborted:options.signal?.aborted===true});}
    let payload;
    try {payload=await response.json();}
    catch {throw new ApiError('Chưa xác nhận được phản hồi từ cửa hàng.',response.status,{uncertain:true});}
    if(!payload||typeof payload!=='object'||Array.isArray(payload))throw new ApiError('Phản hồi từ cửa hàng chưa hợp lệ.',response.status,{uncertain:true});
    if(!response.ok)throw new ApiError(payload.message||'Không thể xử lý yêu cầu.',response.status,{details:payload.details});
    if(payload.success!==true||!Object.hasOwn(payload,'data'))throw new ApiError('Phản hồi từ cửa hàng chưa hợp lệ.',response.status,{uncertain:true});
    return payload.data;
  }
  const changed=()=>new ApiError('Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.',401,{sessionChanged:true,uncertain:true});
  async function rotate(expectedEpoch) {
    if(!refreshing) {
      const current=session();
      refreshing=(async()=>{
        if(!current)throw changed();
        try {
          const next=await send('/api/auth/refresh',{method:'POST',body:{refreshToken:current.refreshToken}});
          if(epoch!==expectedEpoch||session()?.user.id!==current.user.id){
            await send('/api/auth/logout',{method:'POST',body:{refreshToken:next.refreshToken}}).catch(()=>{});throw changed();
          }
          if(!validSession(next)||next.user.id!==current.user.id){
            if(next?.refreshToken)await send('/api/auth/logout',{method:'POST',body:{refreshToken:next.refreshToken}}).catch(()=>{});
            store(null,'expired');throw new ApiError('Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.',403);
          }
          store(next);return next;
        } catch(error){if(epoch===expectedEpoch&&[401,403].includes(error.status))store(null,'expired');throw error;}
      })().finally(()=>{refreshing=null;});
    }
    return refreshing;
  }
  async function request(path,options={}) {
    if(options.auth===false)return send(path,options);
    await ensureOwnership();const current=session(),started=epoch;
    if(!current)throw new ApiError('Vui lòng đăng nhập để tiếp tục.',401);
    let result;
    try {result=await send(path,options,current.accessToken);}
    catch(error) {
      if(error.status!==401)throw error;
      if(epoch!==started||session()?.user.id!==current.user.id)throw changed();
      const next=session()?.accessToken!==current.accessToken?session():await rotate(started);
      try {result=await send(path,options,next.accessToken);}
      catch(retryError){if(retryError.status===401&&epoch===started)store(null,'expired');throw retryError;}
    }
    if(epoch!==started||session()?.user.id!==current.user.id)throw changed();return result;
  }
  function validSession(value){return typeof value?.accessToken==='string'&&value.accessToken.length>0&&typeof value.refreshToken==='string'&&value.refreshToken.length>0&&Number.isSafeInteger(value.user?.id)&&value.user.id>0&&['CUSTOMER','ADMIN','STAFF'].includes(value.user.role);}
  async function accept(result) {
    if(!validSession(result)){
      if(result?.refreshToken)await send('/api/auth/logout',{method:'POST',body:{refreshToken:result.refreshToken}}).catch(()=>{});
      throw new ApiError('Thông tin phiên đăng nhập không hợp lệ.',403);
    }
    store(result,'login');return result;
  }
  function invalidate(current){
    storage.removeItem('ogani.customer.checkout.'+current.user.id);store(null,'logout');
    send('/api/auth/logout',{method:'POST',body:{refreshToken:current.refreshToken},keepalive:true}).catch(()=>{});
  }
  logoutBus?.subscribe(id=>{
    let current;try{current=JSON.parse(storage.getItem(key)||'null');}catch{return;}
    if(validSession(current)&&current.user.id===id&&(current._logoutStamp||'')!==logoutBus.stamp(id))invalidate(current);
  });
  async function verifySession(){
    const current=session(),started=epoch;if(!current)return null;
    try{
      const user=await request('/api/auth/me');
      if(user?.id!==current.user.id||!['CUSTOMER','ADMIN','STAFF'].includes(user.role)){store(null,'expired');return null;}
      const latest=session();if(!latest)return null;
      store({...latest,user:{...latest.user,...user}});return user;
    }catch(error){
      if(!error.sessionChanged&&[401,403].includes(error.status)){
        const latest=session();
        if(latest&&(epoch!==started||latest.user.id!==current.user.id))throw changed();
        if(latest)store(null,'expired');return null;
      }
      throw error;
    }
  }
  return {request,session,verifySession,
    login:async(email,password)=>accept(await send('/api/auth/login',{method:'POST',body:{email,password}})),
    register:async(body)=>accept(await send('/api/auth/register',{method:'POST',body})),
    async logout(){const current=session();if(current)storage.removeItem('ogani.customer.checkout.'+current.user.id);store(null,'logout');if(current){logoutBus?.publish(current.user.id);await send('/api/auth/logout',{method:'POST',body:{refreshToken:current.refreshToken},keepalive:true});}},
    clearSession:()=>store(null,'expired'),
    updateUser(user){const current=session();if(current&&user.id===current.user.id)store({...current,user:{...current.user,...user}});},
    subscribe(listener){listeners.add(listener);return()=>listeners.delete(listener);}
  };
}
