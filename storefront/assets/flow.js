export function positiveId(value) {
  if (!/^[1-9]\d*$/.test(String(value)) || !Number.isSafeInteger(Number(value))) throw new Error('Mã dữ liệu không hợp lệ.');
  return Number(value);
}
export function safeReturnTo(value) {
  const pages=new Set(['index.html','product.html','cart.html','addresses.html','checkout.html','orders.html','order.html']);
  try {
    if(typeof value!=='string'||value.includes('\\')||value.startsWith('/')||value.includes('://'))return 'index.html';
    const url=new URL(value,'https://storefront.invalid/');
    if(url.origin!=='https://storefront.invalid'||!pages.has(url.pathname.slice(1)))return 'index.html';
    return url.pathname.slice(1)+url.search;
  } catch {return 'index.html';}
}
export function checkoutBody(input) {
  const text=value=>typeof value==='string'?value.trim().replace(/\s+/g,' '):'';
  const firstName=text(input.firstName),lastName=text(input.lastName),email=text(input.email);
  if(!firstName||!lastName||firstName.length>60||lastName.length>60||!/^\S+@[^\s@]+$/.test(email)||email.length>150)throw new Error('Vui lòng kiểm tra họ tên và email liên hệ.');
  if(!/^[a-f0-9]{64}$/.test(input.cartToken))throw new Error('Vui lòng tính lại giỏ hàng trước khi đặt.');
  const orderNotes=typeof input.orderNotes==='string'?input.orderNotes.trim():null;
  if(orderNotes?.length>2000)throw new Error('Ghi chú tối đa 2000 ký tự.');
  return {cartToken:input.cartToken,firstName,lastName,email,paymentMethod:'COD',addressId:positiveId(input.addressId),orderNotes:orderNotes||null};
}
export function orderCanCancel(order) {return order?.orderStatus==='PENDING'&&order.paymentStatus==='UNPAID'&&order.paymentMethod==='COD';}
export function serialQueue(identity) {
  let tail=Promise.resolve();
  return work=>{
    const owner=identity();
    const next=tail.then(()=>{if(identity()!==owner)throw Object.assign(new Error('Phiên đăng nhập đã thay đổi.'),{status:401});return work();});
    tail=next.catch(()=>{});return next;
  };
}
export function uuid(crypto=globalThis.crypto) {
  if(crypto?.randomUUID)return crypto.randomUUID();
  if(!crypto?.getRandomValues)throw new Error('Vui lòng dùng localhost hoặc HTTPS để đặt hàng.');
  const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
