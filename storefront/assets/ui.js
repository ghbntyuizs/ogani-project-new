export const config=globalThis.OGANI_STOREFRONT_CONFIG||{apiBase:'http://localhost:8080',currency:'USD'};
export function h(tag,attrs={},...children) {
  const element=document.createElement(tag);
  for(const [name,value] of Object.entries(attrs)) {
    if(value==null)continue;
    if(name.startsWith('on'))element.addEventListener(name.slice(2).toLowerCase(),value);
    else if(name==='class')element.className=value;
    else if(['value','checked','disabled','hidden','selected'].includes(name))element[name]=value;
    else element.setAttribute(name,String(value));
  }
  for(const child of children.flat(Infinity))if(child!=null&&child!==false)element.append(child?.nodeType?child:document.createTextNode(String(child)));
  return element;
}
export const link=(text,href,style='')=>h('a',{href,class:style,onClick:event=>{if(event.currentTarget.getAttribute('aria-disabled')==='true')event.preventDefault();}},text);
export function action(text,handler,style='button') {return h('button',{type:'button',class:style,onClick:handler},text);}
export const heading=(title,subtitle)=>h('div',{class:'page-heading'},h('div',{},h('span',{class:'eyebrow'},'OGANI MARKET'),h('h1',{},title),subtitle?h('p',{class:'muted'},subtitle):null));
export const panel=(...children)=>h('section',{class:'panel'},children);
export function field(label,name,options={}) {
  const {hint,...attrs}=options;
  return h('label',{class:'field'},h('span',{},label),h('input',{name,...attrs}),hint?h('small',{class:'muted'},hint):null);
}
export function notice(text,kind='error') {
  const host=document.getElementById('notice');host.textContent=text;host.className='notice '+kind;host.hidden=!text;
  if(text)host.scrollIntoView({block:'nearest',behavior:'smooth'});
}
export function errorText(error) {
  if(error.status===401)return error.message||'Phiên đăng nhập đã hết hạn.';
  if(error.status===403)return 'Tài khoản không có quyền thực hiện thao tác này.';
  const details=Array.isArray(error.details)?error.details.map(item=>`${item.field}: ${item.message}`).join('; '):'';
  return [error.message||'Chưa xử lý được yêu cầu. Vui lòng thử lại.',details].filter(Boolean).join(' — ');
}
export function loading(root,text='Đang tải dữ liệu…') {root.replaceChildren(h('div',{class:'loading',role:'status'},h('span',{class:'spinner','aria-hidden':'true'}),text));}
export function empty(title,text,cta) {return h('section',{class:'empty'},h('div',{class:'empty-icon','aria-hidden':'true'},'♧'),h('h2',{},title),h('p',{class:'muted'},text),cta);}
export function money(value,currency=config.currency) {
  try{return new Intl.NumberFormat('vi-VN',{style:'currency',currency}).format(Number(value));}
  catch{return `${Number(value).toFixed(2)} ${currency}`;}
}
export function date(value) {const parsed=new Date(value);return Number.isNaN(parsed.getTime())?'—':new Intl.DateTimeFormat('vi-VN',{dateStyle:'medium',timeStyle:'short'}).format(parsed);}
export function imageUrl(value,thumbnail=true) {
  if(!value)return new URL('./images/placeholder.svg',import.meta.url).href;
  if(value.startsWith('img/'))return new URL('./images/'+value.slice(4).replace('product/details/','details/'),import.meta.url).href;
  try {
    const origin=new URL(config.apiBase||location.origin,location.href);
    const url=new URL(value.startsWith('/api/images/')?(config.apiBase||'')+value:value,location.href);
    if(!['http:','https:'].includes(url.protocol))return imageUrl(null);
    if(thumbnail&&url.origin===origin.origin&&url.pathname.startsWith('/api/images/'))url.searchParams.set('size','thumb');
    return url.href;
  }catch{return imageUrl(null);}
}
export function photo(value,alt,style='product-photo',thumbnail=true) {
  return h('img',{src:imageUrl(value,thumbnail),alt,class:style,loading:'lazy',onError:event=>{const img=event.currentTarget;if(!img.dataset.fallback){img.dataset.fallback='1';img.src=imageUrl(null);}}});
}
export const statusNames={PENDING:'Chờ xác nhận',CONFIRMED:'Đã xác nhận',SHIPPING:'Đang giao hàng',COMPLETED:'Hoàn tất',CANCELLED:'Đã hủy'};
export const paymentNames={UNPAID:'Chưa thanh toán',PAID:'Đã thanh toán',REFUNDED:'Đã hoàn tiền'};
export const badge=(value,map=statusNames)=>h('span',{class:'badge state-'+value},map[value]||value);
export function pagination(data,url) {
  if(data.totalPages<2)return null;
  return h('nav',{class:'pagination','aria-label':'Phân trang'},data.page>0?link('← Trước',url(data.page-1),'button secondary'):null,
    h('span',{},`Trang ${data.page+1} / ${data.totalPages}`),data.page+1<data.totalPages?link('Sau →',url(data.page+1),'button secondary'):null);
}
export function addressText(address) {return [address.address,address.ward,address.district,address.city,address.country].filter(Boolean).join(', ');}
export function totals(data,currency=data.currency||config.currency) {
  return h('dl',{class:'totals'},h('dt',{},'Tạm tính'),h('dd',{},money(data.subtotal,currency)),
    Number(data.discountAmount)>0?[h('dt',{},'Giảm giá'),h('dd',{},'− '+money(data.discountAmount,currency))]:null,
    h('dt',{},'Phí giao hàng'),h('dd',{},money(data.shippingFee,currency)),h('dt',{class:'grand'},'Tổng thanh toán'),h('dd',{class:'grand'},money(data.total,currency)));
}
export function setBusy(root,busy) {
  root.querySelectorAll('button,input,select,textarea').forEach(control=>{
    if(busy){if(!Object.hasOwn(control.dataset,'wasDisabled'))control.dataset.wasDisabled=String(control.disabled);control.disabled=true;}
    else if(Object.hasOwn(control.dataset,'wasDisabled')){control.disabled=control.dataset.wasDisabled==='true';delete control.dataset.wasDisabled;}
  });
  root.querySelectorAll('a.button').forEach(anchor=>{
    if(busy){anchor.setAttribute('aria-disabled','true');anchor.dataset.busyLink='1';}
    else if(anchor.dataset.busyLink){anchor.removeAttribute('aria-disabled');delete anchor.dataset.busyLink;}
  });
  root.setAttribute('aria-busy',String(busy));
}
