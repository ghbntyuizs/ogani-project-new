import { h, field, notice, setBusy, errorText } from './ui.js';

export function addressForm(ctx,{address=null,onSaved,onCancel}={}) {
  const form=h('form',{class:'form-stack'});
  form.append(h('div',{class:'form-grid'},field('Tên người nhận','recipientName',{required:true,maxlength:120,autocomplete:'name',value:address?.recipientName||''}),
    field('Số điện thoại','phone',{type:'tel',required:true,maxlength:20,autocomplete:'tel',value:address?.phone||''})),
    h('div',{class:'form-grid'},field('Tỉnh / thành phố','city',{required:true,maxlength:80,autocomplete:'address-level1',value:address?.city||''}),
      field('Quận / huyện','district',{required:true,maxlength:80,autocomplete:'address-level2',value:address?.district||''})),
    field('Phường / xã','ward',{required:true,maxlength:80,autocomplete:'address-level3',value:address?.ward||''}),
    field('Địa chỉ chi tiết','address',{required:true,maxlength:255,autocomplete:'street-address',value:address?.address||'',hint:'Số nhà, tên đường, tòa nhà…'}));
  const defaultFlag=h('input',{type:'checkbox',name:'defaultAddress',checked:address?.defaultAddress===true,disabled:address?.defaultAddress===true});
  form.append(h('label',{class:'checkbox'},defaultFlag,'Đặt làm địa chỉ mặc định'),address?.defaultAddress?h('small',{class:'muted'},'Đây là địa chỉ mặc định. Chọn địa chỉ khác để chuyển mặc định.'):'',
    h('div',{class:'button-row'},h('button',{type:'submit',class:'button'},address?'Lưu thay đổi':'Lưu địa chỉ'),
      onCancel?h('button',{type:'button',class:'button secondary',onClick:onCancel},'Hủy'):null));
  let busy=false;
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(busy||!form.reportValidity())return;
    const values=Object.fromEntries(new FormData(form)),body={};
    for(const key of ['recipientName','phone','city','district','ward','address'])body[key]=values[key].trim();
    body.defaultAddress=defaultFlag.checked;busy=true;setBusy(form,true);notice('');
    try {
      const saved=await ctx.api.request('/api/addresses'+(address?'/'+address.id:''),{method:address?'PUT':'POST',body});
      await onSaved?.(saved);notice(address?'Đã cập nhật địa chỉ.':'Đã lưu địa chỉ.','success');
    } catch(error){ctx.fail(error);}
    finally {busy=false;setBusy(form,false);}
  });
  return form;
}
