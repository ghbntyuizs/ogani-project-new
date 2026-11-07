import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {createApi} from '../../storefront/assets/api.js';
export async function run(){
  const source=(await readFile(new URL('../assets/js/api.js',import.meta.url),'utf8')).replace("import('/assets/api.js')",'Promise.resolve({createApi})');
  const session={accessToken:'old',refreshToken:'refresh-old',user:{id:1,role:'ADMIN'}};
  const ok=data=>Response.json({success:true,data});
  async function setup(handler){
    const values=new Map([['ogani.session',JSON.stringify(session)]]),storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
    const context={window:null,OGANI_API_BASE:'http://localhost:8080',location:{href:'http://localhost:5500/admin/products.html'},URL,FormData,Promise,
      createApi:options=>createApi({...options,storage,fetcher:handler,locks:null,createId:()=> 'owner'})};context.window=context;
    vm.runInNewContext(source,context);return {api:await context.OganiReady,values};
  }
  let calls=[];let {api}=await setup(async(url,init)=>{calls.push({url,...init});return ok({id:1});});
  assert.equal((await api.request('/api/admin/products')).id,1);assert.equal(calls[0].headers.get('Authorization'),'Bearer old');
  let rotations=0;({api}=await setup(async(url,init)=>{if(url.endsWith('/refresh')){rotations++;await new Promise(resolve=>setTimeout(resolve,5));return ok({...session,accessToken:'new'});}return init.headers.get('Authorization')==='Bearer new'?ok('loaded'):Response.json({message:'Expired'},{status:401});}));
  assert.deepEqual(await Promise.all([api.request('/api/admin/products'),api.request('/api/admin/categories')]),['loaded','loaded']);assert.equal(rotations,1);
  assert.equal(api.imageUrl('javascript:alert(1)'),'');assert.equal(api.imageUrl('/api/images/a.png',true),'http://localhost:8080/api/images/a.png?size=thumb');
  assert.equal(api.imageUrl('img/product/product-1.jpg'),'/admin/assets/images/ogani/product/product-1.jpg');
  ({api}=await setup(async()=>Response.json({message:'Denied'},{status:403})));await assert.rejects(api.request('/api/admin/orders'),error=>error.status===403);assert.equal(api.session().user.role,'ADMIN');
  ({api}=await setup(async(url,init)=>{calls.push({url,...init});return ok({imageUrl:'/api/images/test.png'});}));
  assert.equal(await api.upload(new Blob(['image'],{type:'image/png'})),'/api/images/test.png');assert.ok(calls.at(-1).body instanceof FormData);assert.equal(calls.at(-1).headers.get('Content-Type'),null);
  await assert.rejects(api.upload(new Blob(['bad'],{type:'text/plain'})));
  return 'Admin shared API adapter: 6 scenarios passed';
}
