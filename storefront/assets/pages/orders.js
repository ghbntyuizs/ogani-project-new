import { h, link, action, heading, panel, money, date, badge, statusNames, paymentNames, pagination, empty, addressText, totals, notice, setBusy } from '../ui.js';
import { positiveId, orderCanCancel } from '../flow.js';

export async function mount(ctx) {return ctx.page==='order'?detail(ctx):list(ctx);}
async function list(ctx) {
  const params=new URLSearchParams(location.search),query=new URLSearchParams({page:/^\d+$/.test(params.get('page')||'')?params.get('page'):'0',size:'10',sort:params.get('sort')||'createdAt,desc'});
  if(params.get('status'))query.set('status',params.get('status'));
  const data=await ctx.api.request('/api/orders?'+query),form=h('form',{class:'filter-panel'});
  const status=h('select',{name:'status',class:'control'},h('option',{value:''},'Tất cả trạng thái'),Object.entries(statusNames).map(([value,name])=>h('option',{value,selected:value===params.get('status')},name)));
  const sort=h('select',{name:'sort',class:'control'},h('option',{value:'createdAt,desc',selected:query.get('sort')==='createdAt,desc'},'Mới nhất'),h('option',{value:'createdAt,asc',selected:query.get('sort')==='createdAt,asc'},'Cũ nhất'));
  form.append(h('label',{class:'field narrow'},h('span',{},'Trạng thái'),status),h('label',{class:'field narrow'},h('span',{},'Sắp xếp'),sort),h('button',{class:'button',type:'submit'},'Lọc đơn hàng'));
  form.addEventListener('submit',event=>{event.preventDefault();const next=new URLSearchParams(new FormData(form));location.assign('orders.html?'+next);});
  const cards=data.content.map(order=>h('article',{class:'order-card'},h('div',{class:'order-top'},h('div',{},h('p',{class:'order-code'},order.orderCode),h('small',{class:'muted'},date(order.createdAt))),badge(order.orderStatus)),
    h('div',{class:'order-bottom'},h('div',{class:'order-overview'},h('strong',{class:'price'},money(order.total,order.currency)),badge(order.paymentStatus,paymentNames)),link('Xem chi tiết','order.html?id='+order.id,'button secondary small'))));
  ctx.root.replaceChildren(heading('Đơn hàng của tôi','Theo dõi trạng thái và thông tin giao hàng.'),form,
    cards.length?h('div',{},cards):empty('Chưa có đơn hàng phù hợp','Đơn hàng của bạn sẽ xuất hiện tại đây sau khi đặt thành công.',link('Mua sắm ngay','index.html','button')),
    pagination(data,page=>{const next=new URLSearchParams(query);next.set('page',page);next.delete('size');return 'orders.html?'+next;})||'');
}
async function detail(ctx) {
  let id;try{id=positiveId(new URLSearchParams(location.search).get('id'));}catch(error){ctx.root.replaceChildren(empty('Mã đơn hàng không hợp lệ','Vui lòng chọn đơn từ danh sách.',link('Đơn hàng của tôi','orders.html','button')));return;}
  let order=await ctx.api.request('/api/orders/'+id),busy=false;
  async function refresh(){order=await ctx.api.request('/api/orders/'+id);render();}
  function render() {
    const items=panel(h('h2',{},'Sản phẩm đã đặt'),order.items.map(item=>h('div',{class:'order-item'},h('div',{},h('strong',{},item.productName),h('span',{class:'muted'},`${item.quantity} × ${money(item.unitPrice,order.currency)}`)),h('strong',{},money(item.lineTotal,order.currency)))));
    const meta=panel(h('div',{class:'order-meta'},h('div',{},h('h3',{},'Địa chỉ giao hàng'),h('p',{},order.deliveryAddress.recipientName),h('p',{},order.deliveryAddress.phone),h('p',{class:'muted'},addressText(order.deliveryAddress))),
      h('div',{},h('h3',{},'Thông tin đơn'),h('p',{},order.email),h('p',{},date(order.createdAt)),h('p',{},'Thanh toán khi nhận hàng (COD)'),order.orderNotes?h('p',{class:'description'},'Ghi chú: '+order.orderNotes):null)));
    const summary=h('aside',{class:'summary'},h('h2',{},'Tổng đơn hàng'),totals(order),h('div',{class:'button-row'},badge(order.orderStatus),badge(order.paymentStatus,paymentNames)));
    const content=h('div',{},items,meta);
    if(orderCanCancel(order)) {
      const reason=h('textarea',{name:'reason',class:'control',maxlength:200,rows:2,placeholder:'Lý do hủy (không bắt buộc)','aria-label':'Lý do hủy'});
      const button=h('button',{type:'submit',class:'button danger'},'Xác nhận hủy đơn');
      const form=h('form',{class:'cancel-form'},h('h3',{},'Bạn muốn hủy đơn này?'),h('p',{class:'muted'},'Bạn có thể tự hủy khi đơn đang chờ xác nhận và chưa thanh toán.'),reason,h('div',{class:'form-actions'},button));
      form.addEventListener('submit',async event=>{
        event.preventDefault();if(busy||!form.reportValidity()||!confirm('Xác nhận hủy đơn '+order.orderCode+'?'))return;
        const body={reason:reason.value.trim()||null};busy=true;setBusy(ctx.root,true);
        try{order=await ctx.api.request('/api/orders/'+id+'/cancel',{method:'POST',body});render();notice('Đã hủy đơn hàng.','success');ctx.cartChanged();}
        catch(error){ctx.fail(error);try{await refresh();}catch{} }
        finally{busy=false;setBusy(ctx.root,false);}
      });content.append(panel(form));
    }
    ctx.root.replaceChildren(h('nav',{class:'breadcrumb'},link('Đơn hàng của tôi','orders.html'),' / ',order.orderCode),
      h('div',{class:'inline-title'},heading(order.orderCode,'Chi tiết đơn hàng và trạng thái hiện tại.'),action('Làm mới trạng thái',async()=>{try{await refresh();notice('Đã cập nhật trạng thái.','success');}catch(error){ctx.fail(error);}},'button secondary')),
      h('div',{class:'two-column'},content,summary));
  }
  render();if(new URLSearchParams(location.search).get('placed')==='1')notice('Đặt hàng thành công. Bạn có thể theo dõi đơn tại đây.','success');
}
