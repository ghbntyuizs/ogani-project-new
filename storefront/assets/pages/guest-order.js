import {h,link,heading,panel,totals,money,badge,paymentNames,addressText} from '../ui.js';
import {positiveId} from '../flow.js';
export async function mount(ctx){
  const id=positiveId(new URLSearchParams(location.search).get('id'));
  const order=await ctx.guest.request('/api/guest/orders/'+id);
  ctx.root.replaceChildren(heading('Đơn hàng '+order.orderCode,'Đơn đặt hàng không cần tài khoản. Hãy giữ trang này để kiểm tra trong cùng trình duyệt.'),
    panel(h('div',{class:'button-row'},badge(order.orderStatus),badge(order.paymentStatus,paymentNames)),h('h2',{},'Sản phẩm đã đặt'),order.items.map(item=>h('div',{class:'order-item'},item.productName+' × '+item.quantity,money(item.lineTotal,order.currency)))),
    panel(h('h2',{},'Giao đến'),h('p',{},order.deliveryAddress.recipientName),h('p',{},order.deliveryAddress.phone),h('p',{},addressText(order.deliveryAddress)),h('p',{},order.email),totals(order)),
    h('p',{class:'muted'},'Bạn chỉ có thể xem đơn này khi còn phiên khách trong trình duyệt đã đặt hàng. Liên hệ cửa hàng nếu cần thay đổi hoặc hủy đơn.'),link('Tiếp tục mua sắm','index.html','button'));
}
