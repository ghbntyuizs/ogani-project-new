import { h, link, action, heading, panel, field, photo, money, pagination, empty, notice, setBusy, errorText, config } from '../ui.js';
import { positiveId } from '../flow.js';

export async function mount(ctx) {return ctx.page==='product'?product(ctx):catalog(ctx);}
async function add(ctx,id,quantity,button) {
  if(!ctx.requireAuth())return;
  if(ctx.checkout.pending()){location.assign('checkout.html');return;}
  button.disabled=true;
  try {await ctx.cartWrite('/api/cart/items',{method:'POST',body:{productId:positiveId(id),quantity}});notice('Đã thêm sản phẩm vào giỏ hàng.','success');}
  catch(error){ctx.fail(error);}
  finally {button.disabled=false;}
}
function price(product,large=false) {return h('div',{class:'price'},money(product.onSale?product.salePrice:product.price),product.onSale?h('span',{class:'old-price'},money(product.price)):null);}
function productCard(ctx,product) {
  const href='product.html?slug='+encodeURIComponent(product.slug);
  const button=action(product.inStock?'Thêm vào giỏ':'Hết hàng',()=>add(ctx,product.id,1,button));button.disabled=!product.inStock;
  return h('article',{class:'product-card'},product.onSale?h('span',{class:'sale-tag'},`−${product.discountPercent}%`):null,
    h('a',{href,class:'product-image-wrap','aria-label':'Xem '+product.name},photo(product.thumbnailUrl,product.name)),
    h('div',{class:'product-card-body'},h('p',{class:'product-category'},product.categoryName),h('h2',{},link(product.name,href)),price(product),
      h('span',{class:'stock-note'+(product.inStock?'':' out')},product.inStock?'Còn hàng':'Tạm hết hàng'),button));
}
async function catalog(ctx) {
  const params=new URLSearchParams(location.search),query=new URLSearchParams({page:/^\d+$/.test(params.get('page')||'')?params.get('page'):'0',size:'12',sort:params.get('sort')||'latest'});
  for(const key of ['keyword','categoryId','minPrice','maxPrice','onSale'])if(params.get(key))query.set(key,params.get(key));
  const [categories,data]=await Promise.all([ctx.api.request('/api/categories',{auth:false}),ctx.api.request('/api/products?'+query,{auth:false})]);
  const form=h('form',{class:'filter-panel',id:'product-filters'});
  form.append(field('Tìm sản phẩm','keyword',{type:'search',maxlength:200,value:params.get('keyword')||'',placeholder:'Bạn đang tìm gì?',class:'control'}));
  const category=h('select',{name:'categoryId',class:'control'},h('option',{value:''},'Tất cả danh mục'),categories.map(cat=>h('option',{value:cat.id,selected:String(cat.id)===params.get('categoryId')},cat.name)));
  form.append(h('label',{class:'field narrow'},h('span',{},'Danh mục'),category));
  const sort=h('select',{name:'sort',class:'control'},[['latest','Mới nhất'],['price_asc','Giá tăng dần'],['price_desc','Giá giảm dần'],['popular','Bán chạy'],['rating','Đánh giá']].map(([value,title])=>h('option',{value,selected:value===(params.get('sort')||'latest')},title)));
  form.append(h('label',{class:'field narrow'},h('span',{},'Sắp xếp'),sort),field('Giá từ','minPrice',{type:'number',min:0,step:'.01',value:params.get('minPrice')||''}),field('Giá đến','maxPrice',{type:'number',min:0,step:'.01',value:params.get('maxPrice')||''}),h('button',{type:'submit',class:'button'},'Tìm kiếm'));
  const sale=h('input',{type:'checkbox',name:'onSale',checked:params.get('onSale')==='true'});
  const results=h('div',{},h('div',{class:'filter-summary'},h('span',{},`${data.totalElements} sản phẩm`),h('label',{class:'checkbox'},sale,'Đang giảm giá')),
    data.content.length?h('div',{class:'product-grid'},data.content.map(product=>productCard(ctx,product))):empty('Chưa có sản phẩm phù hợp','Thử thay đổi từ khóa hoặc bộ lọc.',link('Xem tất cả','index.html','button secondary')),
    pagination(data,page=>{const url=new URLSearchParams(query);url.set('page',page);url.delete('size');return 'index.html?'+url;}));
  const hero=h('section',{class:'hero'},h('div',{class:'hero-copy'},h('span',{class:'eyebrow'},'TƯƠI NGON MỖI NGÀY'),h('h1',{},'Một giỏ tươi xanh,',h('br'),'trọn bữa ngon lành.'),h('p',{},'Chọn sản phẩm bạn yêu thích và thanh toán khi nhận hàng.'),link('Khám phá sản phẩm','#product-filters','button')),
    h('img',{class:'hero-image',src:new URL('../images/hero/banner.jpg',import.meta.url).href,alt:'Rau củ cho bữa ăn hằng ngày'}));
  ctx.root.replaceChildren(hero,heading('Cửa hàng','Chọn điều tươi ngon cho gia đình bạn.'),form,results);
  function submit(event) {
    event?.preventDefault();if(!form.reportValidity())return;const values=new FormData(form),next=new URLSearchParams();
    if(values.get('minPrice')&&values.get('maxPrice')&&Number(values.get('minPrice'))>Number(values.get('maxPrice'))){notice('Giá tối thiểu phải nhỏ hơn hoặc bằng giá tối đa.');return;}
    for(const [key,value] of values)if(String(value).trim())next.set(key,String(value).trim());if(sale.checked)next.set('onSale','true');
    location.assign('index.html?'+next);
  }
  form.addEventListener('submit',submit);sale.addEventListener('change',()=>submit());
}
async function product(ctx) {
  const slug=new URLSearchParams(location.search).get('slug');if(!slug){ctx.root.replaceChildren(empty('Không tìm thấy sản phẩm','Vui lòng chọn sản phẩm từ cửa hàng.',link('Về cửa hàng','index.html','button')));return;}
  const product=await ctx.api.request('/api/products/'+encodeURIComponent(slug),{auth:false});
  let images=[];try{images=await ctx.api.request('/api/products/'+encodeURIComponent(slug)+'/images',{auth:false});}catch{}
  const main=photo(product.thumbnailUrl||images[0]?.imageUrl,product.name,'detail-photo',false);
  const thumbnails=h('div',{class:'gallery-thumbs'});
  for(const image of images)thumbnails.append(h('button',{class:'thumb-button',type:'button','aria-label':'Xem ảnh sản phẩm',onClick:()=>{main.src=photo(image.imageUrl,product.name,'',false).src;}},photo(image.imageUrl,product.name)));
  const quantity=h('input',{type:'number',name:'quantity',value:1,min:1,max:Math.max(1,product.stockQuantity),step:1,'aria-label':'Số lượng'});
  const addButton=action('Thêm vào giỏ hàng',async()=>{
    const value=Number(quantity.value);if(!quantity.reportValidity()||!Number.isInteger(value)||value<1)return;await add(ctx,product.id,value,addButton);
  });addButton.disabled=!product.inStock;
  const info=h('div',{class:'detail-info'},h('p',{class:'product-category'},product.categoryName),h('h1',{},product.name),price(product,true),
    h('p',{class:'description'},product.shortDescription||'Sản phẩm cho bữa ăn mỗi ngày.'),h('p',{class:'stock-note'+(product.inStock?'':' out')},product.inStock?`Còn ${product.stockQuantity} sản phẩm`:'Sản phẩm tạm hết hàng'),
    h('div',{class:'detail-actions'},quantity,addButton),link('Xem giỏ hàng →','cart.html','text-button'),
    h('p',{class:'summary-hint'},'Tồn kho được kiểm tra lại khi đặt hàng. Thanh toán khi nhận hàng.'),
    product.weightValue?h('p',{class:'muted'},`Khối lượng: ${product.weightValue} ${product.weightUnit}`):null,
    product.shippingInfo?h('p',{class:'muted'},product.shippingInfo):null);
  ctx.root.replaceChildren(h('nav',{class:'breadcrumb','aria-label':'Đường dẫn'},link('Cửa hàng','index.html'),' / ',product.name),
    h('div',{class:'product-detail'},h('div',{},main,thumbnails),info),h('section',{class:'panel section-gap'},h('h2',{},'Thông tin sản phẩm'),h('div',{class:'description'},product.description||product.information||'Thông tin đang được cập nhật.')));
}
