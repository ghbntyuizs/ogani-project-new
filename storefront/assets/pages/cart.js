import { h, link, action, heading, panel, photo, money, empty, notice, setBusy, errorText } from '../ui.js';

export async function mount(ctx) {
  let cart=await ctx.readCart(),busy=false;
  function render() {
    ctx.cartChanged(cart);
    if(!cart.items.length){ctx.root.replaceChildren(heading('Giỏ hàng'),empty('Giỏ hàng của bạn đang trống','Khám phá các sản phẩm và thêm lựa chọn yêu thích.',link('Tiếp tục mua sắm','index.html','button')));return;}
    const rows=panel(...cart.items.map(item=>{
      const input=h('input',{type:'number',value:item.quantity,min:0,max:2147483647,step:1,'aria-label':'Số lượng '+item.productName});
      const update=()=>{
        const quantity=Number(input.value);
        if(input.value===''||!Number.isInteger(quantity)||quantity<0||quantity>2147483647){input.value=item.quantity;notice('Số lượng phải là số nguyên không âm.');return;}
        mutate('/api/cart/items/'+item.itemId,'PUT',{quantity});
      };
      input.addEventListener('change',update);
      const quantity=h('div',{class:'quantity'},action('−',()=>mutate('/api/cart/items/'+item.itemId,'PUT',{quantity:Math.max(0,item.quantity-1)}),'step-minus'),input,action('+',()=>mutate('/api/cart/items/'+item.itemId,'PUT',{quantity:item.quantity+1}),'step-plus'));
      return h('article',{class:'cart-row'},photo(item.thumbnailUrl,item.productName,'cart-image'),h('div',{},h('p',{class:'cart-name'},item.productName),item.available===false?h('p',{class:'muted'},'Không khả dụng / không đủ tồn kho'):null,h('span',{class:'muted'},money(item.unitPrice))),quantity,
        h('div',{class:'line-price'},money(item.lineTotal)),h('button',{type:'button',class:'remove-button','aria-label':'Xóa '+item.productName,onClick:()=>mutate('/api/cart/items/'+item.itemId,'DELETE')},'×'));
    }));
    const summary=h('aside',{class:'summary'},h('h2',{},'Giỏ hàng của bạn'),h('dl',{class:'totals'},h('dt',{},'Tạm tính'),h('dd',{},money(cart.subtotal))),
      h('p',{class:'summary-hint'},'Phí giao hàng và tổng thanh toán được tính ở bước tiếp theo.'),cart.canCheckout===false?h('p',{class:'notice error'},'Có sản phẩm không khả dụng hoặc không đủ tồn kho. Hãy sửa hoặc xóa trước khi đặt hàng.'):link('Tiến hành đặt hàng','checkout.html','button wide'),link('← Tiếp tục mua sắm','index.html','text-button'));
    ctx.root.replaceChildren(heading('Giỏ hàng',`${cart.items.length} loại sản phẩm trong giỏ của bạn.`),h('div',{class:'two-column'},rows,summary));
  }
  async function mutate(path,method,body) {
    if(busy)return;busy=true;setBusy(ctx.root,true);notice('');
    try {cart=await ctx.cartWrite(path,{method,body});render();}
    catch(error){ctx.fail(error);try{cart=await ctx.readCart();render();}catch{} }
    finally {busy=false;setBusy(ctx.root,false);}
  }
  render();
}

