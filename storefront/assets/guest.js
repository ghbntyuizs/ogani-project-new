import {createGuestCart} from './guest-cart.js';
import {createCheckoutState} from './checkout-state.js';
import {uuid} from './flow.js';

export function createGuest({api,storage=localStorage,pendingStorage=storage,locks=globalThis.navigator?.locks}={}) {
  const cart=createGuestCart({storage});let scope=storage.getItem('ogani.guest.scope');
  const request=(path,options={})=>api.request(path,{...options,auth:false});
  const checkout=createCheckoutState({storage:pendingStorage,userId:()=>scope,prefix:'ogani.guest.checkout.',endpoint:'/api/guest/checkout',createKey:uuid,request,
    onConfirmed:async(order,body)=>{const consume=()=>cart.consume(body.items,order.id);return locks?locks.request('ogani.guest.cart',consume):consume();}});
  async function session(){
    const initialize=async()=>{const data=await request('/api/guest/session',{method:'POST'});if(!/^[0-9a-f]{64}$/.test(data?.scope||''))throw new Error('Chưa khởi tạo được phiên khách.');if(scope&&scope!==data.scope&&checkout.pending())throw new Error('Phiên khách đã thay đổi khi đơn chưa rõ kết quả. Hãy liên hệ cửa hàng trước khi đặt lại.');scope=data.scope;storage.setItem('ogani.guest.scope',scope);return scope;};
    return locks?locks.request('ogani.guest.session',initialize):initialize();
  }
  const read=()=>request('/api/guest/cart',{method:'POST',body:{items:cart.lines()}});
  async function write(path,{method,body}={}) {
    const perform=async()=>{
      if(checkout.pending())throw new Error('Hãy kiểm tra kết quả lần đặt hàng trước khi sửa giỏ.');
      const id=method==='POST'?body.productId:Number(path.split('/').pop());
      const old=cart.lines().find(row=>row.productId===id)?.quantity||0;
      const quantity=method==='DELETE'?0:method==='POST'?old+body.quantity:body.quantity;
      cart.set(id,quantity);
      try{const result=await read();if(quantity && !result.items.find(row=>row.productId===id)?.available)throw new Error('Sản phẩm không khả dụng hoặc không đủ tồn kho.');return result;}
      catch(error){cart.set(id,old);throw error;}
    };
    return locks?locks.request('ogani.guest.cart',perform):perform();
  }
  return {cart,checkout,session,scope:()=>scope,read,write,request,
    preview:()=>request('/api/guest/checkout/preview',{method:'POST',body:{items:cart.lines()}})};
}
