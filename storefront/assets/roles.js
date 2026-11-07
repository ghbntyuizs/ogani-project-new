export const isStaff=role=>['ADMIN','STAFF'].includes(role);
export const landing=role=>isStaff(role)?'/':'index.html';
export function adminTransitions(order){
  const paid=order.payment?.status==='PAID',refunded=order.payment?.status==='REFUNDED';
  const fulfill=!refunded&&(order.payment?.method==='COD'||paid);
  const cancel=!paid;
  if(order.orderStatus==='PENDING')return [...(fulfill?['CONFIRMED']:[]),...(cancel?['CANCELLED']:[])];
  if(order.orderStatus==='CONFIRMED')return [...(fulfill?['SHIPPING']:[]),...(cancel?['CANCELLED']:[])];
  if(order.orderStatus==='SHIPPING'&&order.payment?.status==='PAID')return ['COMPLETED'];
  return [];
}
export const canConfirmCod=order=>order.orderStatus==='SHIPPING'&&order.payment?.method==='COD'&&order.payment?.status==='UNPAID';
export function orderQuery(source,page=0){
  const query=new URLSearchParams({page:String(page),size:'20',sort:'createdAt,desc'});
  for(const key of ['keyword','status','paymentStatus','dateFrom','dateTo','size','sort'])if(source.get(key)?.trim())query.set(key,source.get(key).trim());
  if(query.get('dateFrom')&&query.get('dateTo')&&query.get('dateFrom')>query.get('dateTo'))throw new Error('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');
  return query;
}
