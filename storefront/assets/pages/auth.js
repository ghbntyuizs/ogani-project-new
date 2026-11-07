import { h, link, field, panel, notice, setBusy, errorText } from '../ui.js';
import { safeReturnTo } from '../flow.js';
import {isStaff} from '../roles.js';

export async function mount(ctx) {
  const {api,root,page}=ctx;
  const target=safeReturnTo(new URLSearchParams(location.search).get('returnTo'));
  if(api.session()&&page!=='forgot'){location.replace(isStaff(api.session().user.role)?'/':target);return;}
  const isRegister=page==='register',forgot=page==='forgot';
  const title=forgot?'Quên mật khẩu':isRegister?'Tạo tài khoản':'Chào mừng trở lại';
  const form=h('form',{class:'form-stack'});
  if(isRegister)form.append(h('div',{class:'form-grid'},field('Họ / tên đệm','firstName',{required:true,maxlength:60,autocomplete:'given-name'}),field('Tên','lastName',{required:true,maxlength:60,autocomplete:'family-name'})));
  form.append(field('Email','email',{type:'email',required:true,maxlength:150,autocomplete:'email',value:forgot?api.session()?.user.email||'':''}));
  if(!forgot)form.append(field('Mật khẩu','password',{type:'password',required:true,minlength:isRegister?6:1,maxlength:72,autocomplete:isRegister?'new-password':'current-password',hint:isRegister?'Dùng ít nhất 6 ký tự.':null}));
  if(isRegister)form.append(field('Số điện thoại (không bắt buộc)','phone',{type:'tel',maxlength:20,autocomplete:'tel'}));
  const submit=h('button',{type:'submit',class:'button wide'},forgot?'Gửi liên kết đặt mật khẩu':isRegister?'Đăng ký':'Đăng nhập');form.append(submit);
  const card=panel(h('span',{class:'eyebrow'},'TÀI KHOẢN OGANI'),h('h1',{},title),h('p',{class:'muted'},forgot?'Nhập email bạn đã dùng để đăng ký.':'Mua sắm, lưu địa chỉ và theo dõi đơn hàng của bạn.'),form,
    h('div',{class:'auth-links'},link(forgot?'Quay lại đăng nhập':isRegister?'Đã có tài khoản? Đăng nhập':'Chưa có tài khoản? Đăng ký',
      `${isRegister||forgot?'login':'register'}.html?returnTo=${encodeURIComponent(target)}`),!forgot&&!isRegister?link('Quên mật khẩu?','forgot-password.html'):null));
  card.classList.add('auth-card');root.replaceChildren(card);
  if(new URLSearchParams(location.search).get('reason')==='expired')notice('Vui lòng đăng nhập để tiếp tục. Yêu cầu đặt hàng đang chờ sẽ được giữ cho đúng tài khoản.','info');
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(!form.reportValidity())return;
    const values=Object.fromEntries(new FormData(form));
    if(values.password&&new TextEncoder().encode(values.password).length>72){notice('Mật khẩu quá dài. Vui lòng chọn mật khẩu ngắn hơn.');return;}
    setBusy(form,true);notice('');
    try {
      if(forgot){await api.request('/api/auth/forgot-password',{auth:false,method:'POST',body:{email:values.email.trim()}});notice('Nếu email gắn với tài khoản đủ điều kiện, bạn sẽ nhận được liên kết đặt lại mật khẩu. Hãy kiểm tra cả thư rác và đợi một lúc trước khi gửi lại.','success');}
      else {
        if(isRegister)await api.register({firstName:values.firstName.trim(),lastName:values.lastName.trim(),email:values.email.trim(),password:values.password,phone:values.phone.trim()||null});
        else await api.login(values.email.trim(),values.password);
        location.replace(isStaff(api.session()?.user.role)?'/':target);
      }
    } catch(error){notice(errorText(error));}
    finally {setBusy(form,false);}
  });
}
