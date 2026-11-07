import { h, link, action, heading, panel, field, totals, money, addressText, empty, notice, setBusy, errorText } from '../ui.js';
import { checkoutBody } from '../flow.js';
import { addressForm } from '../address-form.js';

export async function mount(ctx) {
  const {api,root,checkout}=ctx;
  let preview=null,addresses=[],selected=null,adding=false,busy=false,contact={};
  const name=(api.session()?.user.fullName||'').trim().split(/\s+/);contact.lastName=name.pop()||'';contact.firstName=name.join(' ');contact.email=api.session()?.user.email||'';
  function rememberContact(){const form=document.getElementById('checkout-contact');if(form)contact={...contact,...Object.fromEntries(new FormData(form))};}
  function showPending(error) {
    root.replaceChildren(h('section',{class:'panel pending-order'},h('span',{class:'eyebrow'},'YÊU CẦU ĐANG CHỜ XÁC NHẬN'),h('h1',{},'Kiểm tra lần đặt hàng vừa gửi'),
      h('p',{class:'muted'},'Kết quả chưa được xác nhận. Hãy thử lại yêu cầu này để nhận đúng đơn hàng, thay vì tạo một lần đặt mới.'),
      error?h('p',{class:'muted'},errorText(error)):null,
      h('div',{class:'button-row'},action('Thử lại đặt hàng',()=>attempt(true)),link('Xem đơn hàng của tôi','orders.html','button secondary'))));
  }
  async function attempt(retry=false) {
    if(busy)return;
    let body;
    if(!retry) {
      const form=document.getElementById('checkout-contact');if(!form?.reportValidity())return;
      if(adding||!selected){notice('Vui lòng chọn hoặc lưu địa chỉ giao hàng.');return;}
      rememberContact();
      try {body=checkoutBody({...contact,addressId:selected,cartToken:preview.cartToken});}catch(error){notice(error.message);return;}
    }
    busy=true;setBusy(root,true);notice('');
    try {
      const order=retry?await checkout.retry():await checkout.place(body);
      location.replace('order.html?id='+encodeURIComponent(order.id)+'&placed=1');
    } catch(error) {
      if(error.status===401){ctx.fail(error);return;}
      if(checkout.pending())showPending(error);
      else {
        notice(error.status===409?'Giỏ hàng, giá hoặc tồn kho đã thay đổi. Vui lòng kiểm tra lại và xác nhận tổng tiền mới.':errorText(error));
        await load();
      }
    } finally {busy=false;setBusy(root,false);}
  }
  function render() {
    const form=h('form',{id:'checkout-contact',class:'form-stack'});
    form.append(h('div',{class:'form-grid'},field('Họ / tên đệm','firstName',{required:true,maxlength:60,autocomplete:'given-name',value:contact.firstName||''}),
      field('Tên','lastName',{required:true,maxlength:60,autocomplete:'family-name',value:contact.lastName||''})),
      field('Email liên hệ','email',{type:'email',required:true,maxlength:150,autocomplete:'email',value:contact.email||''}),
      h('label',{class:'field'},h('span',{},'Ghi chú đơn hàng (không bắt buộc)'),h('textarea',{name:'orderNotes',maxlength:2000,value:contact.orderNotes||'',class:'control',rows:3})));
    form.addEventListener('submit',event=>{event.preventDefault();attempt();});
    const options=h('div',{class:'form-stack'},addresses.map(address=>h('label',{class:'address-option'},h('input',{type:'radio',name:'delivery',value:address.id,checked:address.id===selected,onChange:()=>{selected=address.id;}}),
      h('div',{},h('strong',{},address.recipientName),address.defaultAddress?h('span',{class:'badge'},'Mặc định'):null,h('p',{},address.phone),h('p',{class:'muted'},addressText(address))))));
    const addButton=action('＋ Thêm địa chỉ mới',()=>{rememberContact();adding=true;render();},'text-button');addButton.disabled=addresses.length>=10||adding;
    const addressPanel=panel(h('h2',{class:'step-title'},h('span',{class:'step-number'},'2'),'Giao đến địa chỉ'),addresses.length?options:h('p',{class:'muted'},'Bạn chưa có địa chỉ. Lưu một địa chỉ để tiếp tục.'),addButton,
      addresses.length>=10?h('p',{class:'summary-hint'},'Đã đạt giới hạn 10 địa chỉ. Bạn có thể sửa địa chỉ tại trang Địa chỉ.'):null,
      adding?addressForm(ctx,{onSaved:async saved=>{rememberContact();addresses=await api.request('/api/checkout/addresses');selected=saved.id;adding=false;render();},onCancel:addresses.length?()=>{rememberContact();adding=false;render();}:null}):null);
    if(adding)options.querySelectorAll('input').forEach(input=>{input.disabled=true;});
    const submit=h('button',{type:'submit',form:'checkout-contact',class:'button wide',disabled:adding},'Xác nhận đặt hàng COD');
    const summary=h('aside',{class:'summary'},h('h2',{},'Đơn hàng của bạn'),h('ul',{class:'checkout-items'},preview.items.map(item=>h('li',{},h('span',{},`${item.productName} × ${item.quantity}`),h('strong',{},money(item.lineTotal,preview.currency))))),
      totals(preview),submit,h('p',{class:'summary-hint'},'Thanh toán tiền hàng khi nhận. Tổng tiền và tồn kho được kiểm tra lại khi đặt hàng.'));
    root.replaceChildren(heading('Đặt hàng','Kiểm tra sản phẩm, địa chỉ và tổng tiền trước khi xác nhận.'),h('div',{class:'two-column'},h('div',{},
      panel(h('h2',{class:'step-title'},h('span',{class:'step-number'},'1'),'Thông tin liên hệ'),form),addressPanel,
      panel(h('h2',{class:'step-title'},h('span',{class:'step-number'},'3'),'Phương thức thanh toán'),h('div',{class:'payment-card'},h('strong',{},'Thanh toán khi nhận hàng (COD)'),h('p',{class:'muted'},'Bạn thanh toán khi đơn hàng được giao đến.')))),summary));
  }
  async function load() {
    try {
      [preview,addresses]=await Promise.all([api.request('/api/checkout/preview',{method:'POST'}),api.request('/api/checkout/addresses')]);
      if(!addresses.some(address=>address.id===selected))selected=(addresses.find(address=>address.defaultAddress)||addresses[0])?.id||null;
      adding=addresses.length===0;render();
    } catch(error) {
      if(error.status===401){ctx.fail(error);return;}
      root.replaceChildren(heading('Đặt hàng'),empty('Chưa thể tính đơn hàng',errorText(error),h('div',{class:'button-row'},link('Kiểm tra giỏ hàng','cart.html','button'),action('Thử lại',load,'button secondary'))));
    }
  }
  if(checkout.pending()){showPending();await attempt(true);}
  else await load();
}
