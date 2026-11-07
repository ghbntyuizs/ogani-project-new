import {uuid} from './flow.js';
// Only logout markers are shared. Access/refresh tokens stay in sessionStorage.
export function createLogoutBus({storage=globalThis.localStorage,events=globalThis.window,nonce=uuid}={}){
  if(!storage||!events?.addEventListener)return null;
  const prefix='ogani.logout.',listeners=new Set();
  events.addEventListener('storage',event=>{
    if(event.key?.startsWith(prefix)&&event.newValue){const id=Number(event.key.slice(prefix.length));if(Number.isSafeInteger(id)&&id>0)for(const listener of listeners)listener(id);}
  });
  return {
    stamp(id){try{return storage.getItem(prefix+id)||'';}catch{return '';}},
    publish(id){try{storage.setItem(prefix+id,nonce());}catch{}for(const listener of listeners)listener(id);},
    subscribe(listener){listeners.add(listener);return()=>listeners.delete(listener);}
  };
}
