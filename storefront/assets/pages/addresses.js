import { h, action, heading, panel, empty, badge, addressText, notice, setBusy } from '../ui.js';
import { addressForm } from '../address-form.js';

export async function mount(ctx) {
  let addresses=await ctx.api.request('/api/checkout/addresses'),editing=null,adding=addresses.length===0,busy=false;
  async function reload(){addresses=await ctx.api.request('/api/checkout/addresses');editing=null;adding=false;render();}
  async function mutate(address,remove=false) {
    if(busy)return;if(remove&&!confirm('Xóa địa chỉ này? Thông tin giao hàng trong các đơn cũ vẫn được giữ.'))return;
    busy=true;setBusy(ctx.root,true);notice('');
    try {await ctx.api.request(`/api/addresses/${address.id}`+(remove?'':'/default'),{method:remove?'DELETE':'PATCH'});await reload();notice(remove?'Đã xóa địa chỉ.':'Đã đổi địa chỉ mặc định.','success');}
    catch(error){ctx.fail(error);}finally{busy=false;setBusy(ctx.root,false);}
  }
  function render() {
    const add=action('＋ Thêm địa chỉ',()=>{adding=true;editing=null;render();document.getElementById('address-editor')?.scrollIntoView({block:'start',behavior:'smooth'});});add.disabled=addresses.length>=10||adding||!!editing;
    const list=h('div',{class:'address-grid'},addresses.map(address=>h('article',{class:'address-card'+(address.defaultAddress?' selected':'')},
      h('div',{class:'inline-title'},h('h3',{},address.recipientName),address.defaultAddress?h('span',{class:'badge'},'Mặc định'):null),
      h('p',{},address.phone),h('p',{class:'muted'},addressText(address)),h('div',{class:'button-row'},
        action('Sửa',()=>{editing=address;adding=false;render();document.getElementById('address-editor')?.scrollIntoView({block:'start',behavior:'smooth'});},'button small secondary'),
        !address.defaultAddress?action('Chọn mặc định',()=>mutate(address),'button small secondary'):null,action('Xóa',()=>mutate(address,true),'button small danger')))));
    const editor=adding||editing?panel(h('h2',{},editing?'Sửa địa chỉ':'Thêm địa chỉ mới'),addressForm(ctx,{address:editing,onSaved:reload,onCancel:()=>{editing=null;adding=false;render();}})):null;
    if(editor){editor.id='address-editor';setBusy(list,true);}
    ctx.root.replaceChildren(h('div',{class:'inline-title'},heading('Địa chỉ giao hàng',`${addresses.length}/10 địa chỉ đã lưu. Địa chỉ đầu tiên tự động là mặc định.`),add),
      addresses.length?list:empty('Chưa có địa chỉ','Thêm địa chỉ để đặt hàng thuận tiện hơn.'),editor?h('div',{class:'section-gap'},editor):'');
  }
  render();
}
