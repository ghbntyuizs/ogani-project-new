import { createApi } from './api.js';
import { createCheckoutState } from './checkout-state.js';
import { safeReturnTo, uuid, serialQueue } from './flow.js';
import {isStaff} from './roles.js';
import {createGuest} from './guest.js';
import { h, link, action, config, notice, errorText, loading, empty } from './ui.js';

const api=createApi({base:config.apiBase});
const page=document.body.dataset.page,root=document.getElementById('app');
const privatePages=new Set(['addresses','orders','order']);
const customerPages=new Set(['cart','addresses','checkout','orders','order']);
const currentPage=()=>location.pathname.split('/').pop()||'index.html';
const returnPath=()=>currentPage()+location.search;
const loginUrl=()=>`login.html?returnTo=${encodeURIComponent(safeReturnTo(returnPath()))}`;
const customerCheckout=createCheckoutState({storage:sessionStorage,userId:()=>api.session()?.user.id,createKey:()=>uuid(),request:api.request});
const guest=createGuest({api});
const checkout={pending:()=> (api.session()?customerCheckout:guest.checkout).pending(),clear:()=> (api.session()?customerCheckout:guest.checkout).clear()};
let cartCount=0;
let cartRevision=0;
let generation=0;
const queueCart=serialQueue(()=>generation);
function header() {
  const session=api.session(),nav=h('nav',{class:'main-nav','aria-label':'Điều hướng chính'});
  const staff=isStaff(session?.user.role);
  for(const [label,href,pages] of (staff?[['Cửa hàng','index.html',['catalog','product']],['Quản trị','/',[]]]:[['Cửa hàng','index.html',['catalog','product']],['Giỏ hàng','cart.html',['cart','checkout']],['Địa chỉ','addresses.html',['addresses']],['Đơn hàng','orders.html',['orders','order']]])) {
    const anchor=link(label,href,pages.includes(page)?'active':'');
    if(href==='cart.html')anchor.append(h('span',{class:'cart-count',id:'cart-count','aria-label':'Số sản phẩm trong giỏ'},checkout.pending()?'…':cartCount));nav.append(anchor);
  }
  const account=h('div',{class:'account-nav'});
  if(session)account.append(h('span',{class:'account-name',title:session.user.email},session.user.fullName||session.user.email),action('Đăng xuất',async()=>{
    if(checkout.pending()&&!confirm('Yêu cầu đặt hàng chưa xác nhận. Bạn có thể kiểm tra Đơn hàng sau khi đăng nhập lại. Tiếp tục đăng xuất?'))return;
    checkout.clear();try{await api.logout();}catch{}location.replace('index.html');
  },'text-button'));
  else account.append(link('Đăng nhập',loginUrl(),'text-button'),link('Đăng ký','register.html','button small secondary'));
  document.getElementById('site-header').replaceChildren(h('header',{class:'site-header'},h('div',{class:'container header-inner'},
    h('a',{class:'brand',href:'index.html','aria-label':'Ogani — Cửa hàng'},'ogani',h('i',{},'.')),nav,account)));
}
function cartChanged(cart) {
  if(cart){cartRevision++;cartCount=(cart.items||[]).reduce((sum,item)=>sum+Number(item.quantity||0),0);header();}
  else if(api.session()?.user.role==='CUSTOMER'&&!checkout.pending()){
    const revision=cartRevision;api.request('/api/cart').then(cart=>{if(revision===cartRevision)cartChanged(cart);}).catch(()=>{});
  }
  else if(!api.session()){cartCount=guest.cart.lines().reduce((sum,row)=>sum+row.quantity,0);header();}
}
function fail(error) {
  if(error.aborted)return;
  if(error.status===401){location.assign(loginUrl()+'&reason=expired');return;}
  notice(errorText(error));
}
const ctx={api,root,page,guest,checkout:customerCheckout,cartChanged,fail,loginUrl,
  readCart:()=>api.session()?api.request('/api/cart'):guest.read(),
  cartWrite:(path,options)=>queueCart(async()=>{if(checkout.pending()){location.assign('checkout.html');throw new Error('Vui lòng xác nhận kết quả đặt hàng trước khi sửa giỏ.');}const cart=await (api.session()?api.request(path,options):guest.write(path,options));cartChanged(cart);return cart;}),
  requireAuth(){if(isStaff(api.session()?.user.role)){notice('Giỏ hàng và đặt hàng dành cho tài khoản Customer. Tài khoản quản trị của bạn vẫn được giữ.','info');return false;}return true;}};
api.subscribe(reason=>{
  if(reason!=='refresh')generation++;
  if(!api.session())cartCount=0;header();
  if(reason==='logout'){loading(root,'Đang đăng xuất…');location.replace('index.html');}
  if(reason==='expired'&&privatePages.has(page))location.replace(loginUrl()+'&reason=expired');
});
document.getElementById('site-footer').replaceChildren(h('footer',{class:'site-footer'},h('div',{class:'container footer-inner'},
  h('div',{},link('ogani.','index.html','brand'),h('p',{class:'muted'},'Chọn điều tươi ngon cho bữa ăn mỗi ngày.')),
  h('div',{},h('p',{},'Thanh toán khi nhận hàng'),h('p',{class:'muted'},'Theo dõi đơn hàng ngay trong tài khoản của bạn.')))));

async function start() {
  cartCount=api.session()?0:guest.cart.lines().reduce((sum,row)=>sum+row.quantity,0);
  header();loading(root);
  if(api.session()) {
    try {
      await api.verifySession();
    } catch(error) {
      if(customerPages.has(page)||['login','register'].includes(page)) {
        if(error.status===401||error.status===403){location.replace(loginUrl()+'&reason=expired');return;}
        root.replaceChildren(empty('Chưa kết nối được tài khoản',errorText(error),action('Thử lại',()=>location.reload())));return;
      }
    }
  }
  if(!api.session()&&['cart','checkout','guest-order'].includes(page)){
    try{await guest.session();}catch(error){root.replaceChildren(empty('Chưa kết nối được phiên khách',errorText(error),action('Thử lại',()=>location.reload())));return;}
  }
  if(privatePages.has(page)&&!api.session()){location.replace(loginUrl());return;}
  if(customerPages.has(page)&&isStaff(api.session()?.user.role)){root.replaceChildren(empty('Chức năng dành cho Customer','Bạn đang đăng nhập tài khoản quản trị. Bạn có thể xem sản phẩm hoặc quay lại quản trị.',h('div',{class:'button-row'},link('Cửa hàng','index.html','button'),link('Quản trị','/','button secondary'))));return;}
  if(['cart','addresses'].includes(page)&&checkout.pending()){
    root.replaceChildren(empty('Đang có lần đặt hàng chờ xác nhận','Vui lòng kiểm tra kết quả lần đặt trước khi thay đổi giỏ hàng hoặc địa chỉ.',h('div',{class:'button-row'},link('Kiểm tra đặt hàng','checkout.html','button'),link('Xem đơn hàng','orders.html','button secondary'))));return;
  }
  const modules={catalog:'catalog',product:'catalog',cart:'cart',addresses:'addresses',checkout:api.session()?'checkout':'guest-checkout','guest-order':'guest-order',orders:'orders',order:'orders',login:'auth',register:'auth',forgot:'auth'};
  try {const module=await import(`./pages/${modules[page]}.js`);await module.mount(ctx);cartChanged();}
  catch(error){root.replaceChildren(empty(error.status===404?'Không tìm thấy dữ liệu':'Chưa tải được trang',errorText(error),error.status===404?link('Quay lại',page==='order'?'orders.html':'index.html','button'):action('Thử lại',()=>location.reload())));fail(error);}
}
window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});
start();

