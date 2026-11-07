import {h,link,action,heading,panel,field,totals,money,empty,notice,setBusy,errorText} from '../ui.js';

export async function mount(ctx){
  const {guest,root}=ctx,checkout=guest.checkout;let preview,busy=false,contact={};
  function remember(){const form=root.querySelector('form');if(form)contact=Object.fromEntries(new FormData(form));}
  function pending(error){root.replaceChildren(panel(heading('Kiểm tra lần đặt hàng vừa gửi'),h('p',{},'Kết quả chưa được xác nhận. Thử lại cùng yêu cầu để tránh đặt trùng.'),error?h('p',{},errorText(error)):null,action('Thử lại đặt hàng',()=>attempt(true))));}
  async function attempt(retry=false){
    if(busy)return;
    let body;
    if(!retry){const form=root.querySelector('form');if(!form.reportValidity())return;remember();
      const {firstName,lastName,email,phone,city,district,ward,address,orderNotes}=contact;
      body={scope:guest.scope(),cartToken:preview.cartToken,items:guest.cart.lines(),firstName:firstName.trim(),lastName:lastName.trim(),email:email.trim(),paymentMethod:'COD',
        deliveryAddress:{recipientName:(firstName+' '+lastName).trim(),phone,city,district,ward,address,defaultAddress:false},orderNotes:orderNotes?.trim()||null};
    }
    busy=true;setBusy(root,true);notice('');
    try{const order=retry?await checkout.retry():await checkout.place(body);ctx.cartChanged();location.replace('guest-order.html?id='+order.id);}
    catch(error){if(checkout.pending())pending(error);else{notice(error.status===409?'Giá hoặc tồn kho đã thay đổi. Hãy kiểm tra lại đơn hàng.':errorText(error));await load();}}
    finally{busy=false;setBusy(root,false);}
  }
  function render(){
    const form=h('form',{id:'guest-checkout',class:'form-stack'});
    for(const [label,name,max,type,autocomplete] of [
      ['Họ / tên đệm','firstName',60,'text','given-name'],['Tên','lastName',60,'text','family-name'],['Email liên hệ','email',150,'email','email'],
      ['Số điện thoại','phone',20,'tel','tel'],['Tỉnh / thành phố','city',80,'text','address-level1'],['Quận / huyện','district',80,'text','address-level2'],
      ['Phường / xã','ward',80,'text','address-level3'],['Địa chỉ chi tiết','address',255,'text','street-address']])
      form.append(field(label,name,{required:true,maxlength:max,type,autocomplete,value:contact[name]||'',...(name==='phone'?{pattern:'(0[35789][0-9]{8}|02[0-9]{9}|\\+84[35789][0-9]{8})',hint:'Số điện thoại Việt Nam, ví dụ 0901234567.'}:{})}));
    form.append(field('Ghi chú (không bắt buộc)','orderNotes',{maxlength:2000,value:contact.orderNotes||''}));
    form.addEventListener('submit',event=>{event.preventDefault();attempt();});
    const summary=h('aside',{class:'summary'},h('h2',{},'Đơn hàng của bạn'),h('ul',{class:'checkout-items'},preview.items.map(item=>h('li',{},h('span',{},item.productName+' × '+item.quantity),money(item.lineTotal,preview.currency)))),totals(preview),
      h('button',{type:'submit',form:'guest-checkout',class:'button wide'},'Xác nhận đặt hàng COD'),h('p',{class:'summary-hint'},'Thanh toán khi nhận hàng. Tồn kho được kiểm tra lại khi xác nhận.'));
    root.replaceChildren(heading('Đặt hàng không cần tài khoản','Vui lòng nhập thông tin người nhận và địa chỉ giao hàng. Không lưu địa chỉ vào tài khoản.'),h('div',{class:'two-column'},panel(form),summary));
  }
  async function load(){try{preview=await guest.preview();render();}catch(error){root.replaceChildren(heading('Đặt hàng'),empty('Chưa thể tính đơn hàng',errorText(error),h('div',{class:'button-row'},link('Kiểm tra giỏ hàng','cart.html','button'),action('Thử lại',load,'button secondary'))));}}
  if(checkout.pending()){pending();await attempt(true);}else await load();
}
