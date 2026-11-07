import {h,setBusy} from '/shop/assets/ui.js';
import {positiveId} from '/shop/assets/flow.js';
import {adminTransitions,canConfirmCod,orderQuery} from '/shop/assets/roles.js';

const statuses=['PENDING','CONFIRMED','SHIPPING','COMPLETED','CANCELLED'];
const payments=['UNPAID','PAID','REFUNDED'];
const names={PENDING:'Chờ xác nhận',CONFIRMED:'Đã xác nhận',SHIPPING:'Đang giao',COMPLETED:'Hoàn tất',CANCELLED:'Đã hủy',UNPAID:'Chưa thanh toán',PAID:'Đã thanh toán',REFUNDED:'Đã hoàn tiền'};
const badge=value=>h('span',{class:'badge-table '+({PAID:'success',COMPLETED:'success',CANCELLED:'danger',SHIPPING:'info',CONFIRMED:'info'}[value]||'pending')},names[value]||value||'—');
const amount=(value,currency)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:currency||'USD'}).format(value||0);
const date=value=>value?new Date(value).toLocaleString('vi-VN'):'—';
const button=(title,fn,style='primary')=>h('button',{type:'button',class:'btn btn-'+style,onClick:fn},title);
const card=(title,...children)=>h('section',{class:'card border-light shadow-sm p-4 mb-4'},h('h2',{class:'h5 mb-3'},title),...children);
const entry=(label,value)=>h('div',{class:'d-flex justify-content-between gap-3 py-2 border-bottom'},h('span',{},label),h('span',{},value??'—'));
function control(label,name,attrs={}){return h('label',{class:'form-label'},label,h('input',{name,'aria-label':label,class:'form-control',...attrs}));}
function select(label,name,values,current){return h('label',{class:'form-label'},label,h('select',{name,'aria-label':label,class:'form-select'},values.map(([value,text])=>h('option',{value,selected:value===current},text))));}
function table(headers,rows){return h('div',{class:'table-responsive'},h('table',{class:'table-custom'},h('thead',{},h('tr',{},headers.map(title=>h('th',{},title)))),h('tbody',{},rows)));}
function loading(root){root.replaceChildren(h('p',{class:'p-4',role:'status'},'Đang tải đơn hàng…'));}
export async function start(page,api,ui){
  const root=document.getElementById('orders-api-root');
  const css=h('link',{rel:'stylesheet',href:'/admin/assets/css/orders.css'});document.head.append(css);
  // Template notifications are not live order notifications.
  document.querySelectorAll('.notification-item').forEach(item=>item.remove());
  const fail=error=>{if(error.status===401||!api.session()){location.replace('/login.html');return;}ui.message(error.message||'Không tải được đơn hàng.');};
  if(page==='orders.html')await list(root,api,ui,fail);else await detail(root,api,ui,fail);
}
async function list(root,api,ui,fail){
  async function load(){
    loading(root);ui.message('');
    try {
      const source=new URLSearchParams(location.search),page=/^\d+$/.test(source.get('page')||'')?Number(source.get('page')):0;
      const query=orderQuery(source,page),data=await api.request('/api/admin/orders?'+query);
      const form=h('form',{class:'order-filters'},control('Mã đơn / tên / email','keyword',{value:query.get('keyword')||'',maxlength:200,type:'search'}),
        select('Trạng thái đơn','status',[['','Tất cả'],...statuses.map(value=>[value,names[value]])],query.get('status')||''),
        select('Thanh toán','paymentStatus',[['','Tất cả'],...payments.map(value=>[value,names[value]])],query.get('paymentStatus')||''),
        control('Từ ngày','dateFrom',{type:'date',value:query.get('dateFrom')||''}),control('Đến ngày','dateTo',{type:'date',value:query.get('dateTo')||''}),
        select('Sắp xếp','sort',[['createdAt,desc','Mới nhất'],['createdAt,asc','Cũ nhất']],query.get('sort')),
        select('Số dòng','size',[['10','10'],['20','20'],['50','50'],['100','100']],query.get('size')),
        h('button',{class:'btn btn-primary',type:'submit'},'Lọc đơn hàng'),h('a',{class:'btn btn-outline-secondary',href:'orders.html'},'Xóa bộ lọc'));
      form.addEventListener('submit',event=>{event.preventDefault();if(!form.reportValidity())return;try{location.assign('orders.html?'+orderQuery(new URLSearchParams(new FormData(form))));}catch(error){ui.message(error.message);}});
      const rows=data.content.map(order=>h('tr',{},h('td',{},h('a',{href:'order-detail.html?id='+order.id},order.orderCode)),h('td',{},h('strong',{},order.customerName||'Khách hàng đã xóa'),h('div',{class:'text-muted'},order.customerEmail||'—')),
        h('td',{},date(order.createdAt)),h('td',{},order.paymentMethod),h('td',{},badge(order.paymentStatus)),h('td',{},amount(order.total,order.currency)),h('td',{},badge(order.orderStatus)),h('td',{},h('a',{href:'order-detail.html?id='+order.id,class:'btn btn-sm btn-outline-primary'},'Chi tiết'))));
      const paging=h('nav',{class:'d-flex gap-3 align-items-center mt-3 flex-wrap','aria-label':'Phân trang'},h('span',{},`${data.totalElements} đơn — Trang ${data.totalPages?data.page+1:0}/${data.totalPages}`));
      for(const [title,target,disabled] of [['← Trước',data.page-1,data.page===0],['Sau →',data.page+1,data.page+1>=data.totalPages]]){const b=button(title,()=>location.assign('orders.html?'+orderQuery(query,target)),'outline-secondary');b.disabled=disabled;paging.append(b);}
      root.replaceChildren(card('Danh sách đơn hàng',form,button('Làm mới',load,'outline-secondary'),rows.length?table(['Mã đơn','Customer','Ngày tạo','Phương thức','Thanh toán','Tổng tiền','Trạng thái','Thao tác'],rows):h('p',{class:'p-4'},'Không có đơn hàng phù hợp.'),paging));
    }catch(error){root.replaceChildren(card('Chưa tải được danh sách',button('Thử lại',load)));fail(error);}
  }
  await load();
}
async function detail(root,api,ui,fail){
  let id;try{id=positiveId(new URLSearchParams(location.search).get('id'));}catch{root.replaceChildren(card('Mã đơn không hợp lệ',h('a',{href:'orders.html'},'Về danh sách đơn hàng')));return;}
  let order,busy=false;
  async function load(){loading(root);try{order=await api.request('/api/admin/orders/'+id);render();return true;}catch(error){root.replaceChildren(card(error.status===404?'Không tìm thấy đơn hàng':'Chưa tải được chi tiết',button('Thử lại',load),h('a',{href:'orders.html'},'Về danh sách')));fail(error);return false;}}
  async function mutate(path,body){
    if(busy)return;
    if(!confirm(path==='payment-status'?'Xác nhận bạn đã thu đủ tiền COD cho đơn này?':'Xác nhận cập nhật đơn '+order.orderCode+'?'))return;
    busy=true;setBusy(root,true);ui.message('');
    try {
      await api.request(`/api/admin/orders/${id}/${path}`,{method:'PATCH',body});
      if(await load())ui.message('Đã cập nhật đơn hàng.','success');
    }catch(error){
      await load();
      if(error.status===409)ui.message('Đơn hàng đã thay đổi hoặc thao tác không còn hợp lệ. Đã tải lại dữ liệu; kiểm tra trước khi xác nhận lại.');
      else {fail(error);if(error.uncertain)ui.message('Chưa xác nhận được kết quả thao tác. Kiểm tra trạng thái mới trước khi thực hiện lại.');}
    }finally{busy=false;setBusy(root,false);}
  }
  function render(){
    document.querySelector('.page-subtitle').textContent=order.orderCode;
    const crumb=document.querySelector('.breadcrumb-item.active');if(crumb)crumb.textContent=order.orderCode;
    const items=table(['Tên sản phẩm (snapshot)','Đơn giá','Số lượng','Thành tiền'],order.items.map(item=>h('tr',{},h('td',{},item.productName),h('td',{},amount(item.unitPrice,order.currency)),h('td',{},item.quantity),h('td',{},amount(item.lineTotal,order.currency)))));
    const totals=h('div',{class:'order-totals'},entry('Tạm tính',amount(order.subtotal,order.currency)),entry('Giảm giá',amount(order.discountAmount,order.currency)),entry('Coupon',order.coupon?.code||'Không có'),entry('Phí giao hàng',amount(order.shippingFee,order.currency)),entry('Tổng thanh toán',amount(order.total,order.currency)));
    const history=h('ol',{class:'order-history'},order.statusHistory.map(event=>h('li',{},h('strong',{},(event.fromStatus?event.fromStatus+' → ':'')+event.toStatus),h('div',{class:'text-muted'},date(event.changedAt)+' · '+(event.changedBy?.name||'Hệ thống')),h('p',{class:'order-text'},event.note||''))));
    const a=order.deliveryAddress,c=order.customer,p=order.payment;
    const delivery=card('Địa chỉ giao hàng (snapshot)',h('p',{},a.recipientName),h('p',{},a.phone),h('p',{class:'order-text'},[a.address,a.ward,a.district,a.city,a.state,a.postcode,a.country].filter(Boolean).join(', ')),h('p',{class:'order-text'},'Ghi chú: '+(order.orderNotes||'—')));
    const customer=card('Customer',entry('Tên',[c?.firstName,c?.lastName].filter(Boolean).join(' ')||'—'),entry('Email',c?.email),entry('Điện thoại',c?.phone),entry('User ID',c?.userId));
    const payment=card('Thanh toán',entry('Phương thức',p.method),entry('Trạng thái',badge(p.status)),entry('Thời điểm thu tiền',date(p.paidAt)),entry('Người xác nhận (ID)',p.confirmedBy),entry('Mã giao dịch',p.transactionRef));
    if(canConfirmCod(order))payment.append(button('Xác nhận đã thu tiền COD',()=>mutate('payment-status',{paymentStatus:'PAID',version:order.version})));
    const actions=card('Xử lý đơn hàng',entry('Trạng thái',badge(order.orderStatus)),entry('Version',order.version));
    const allowed=adminTransitions(order);
    if(allowed.length){
      const form=h('form',{class:'order-actions'},select('Chuyển sang','status',allowed.map(value=>[value,names[value]]),allowed[0]),control('Ghi chú trạng thái','note',{maxlength:200}),h('button',{type:'submit',class:'btn btn-primary'},'Cập nhật trạng thái'));
      form.addEventListener('submit',event=>{event.preventDefault();if(!form.reportValidity())return;const data=new FormData(form);mutate('status',{status:data.get('status'),note:data.get('note').trim()||null,version:order.version});});actions.append(form);
    }else actions.append(h('p',{class:'text-muted mt-3'},order.orderStatus==='SHIPPING'&&p.status==='UNPAID'?'Cần xác nhận thu tiền COD trước khi hoàn tất.':'Không có chuyển trạng thái khả dụng.'));
    root.replaceChildren(h('div',{class:'d-flex gap-3 mb-3'},h('a',{class:'btn btn-outline-secondary',href:'orders.html'},'← Danh sách đơn'),button('Làm mới trạng thái',load,'outline-secondary')),
      h('div',{class:'row g-4'},h('div',{class:'col-lg-8'},card(order.orderCode,items,totals),card('Lịch sử trạng thái',history)),h('div',{class:'col-lg-4'},actions,payment,customer,delivery)));
  }
  await load();
}
