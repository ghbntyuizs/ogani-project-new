import {positiveId} from './flow.js';
export function createGuestCart({storage=localStorage}={}){
  const key='ogani.guest.cart';
  const quantity=value=>{if(!Number.isSafeInteger(value)||value<0||value>2147483647)throw new Error('Số lượng phải là số nguyên hợp lệ.');return value;};
  function state(){try{const data=JSON.parse(storage.getItem(key)||'[]');const rows=Array.isArray(data)?data:data.items;if(!Array.isArray(rows)||rows.length>100)throw new Error();const ids=new Set();for(const row of rows){positiveId(row.productId);if(quantity(row.quantity)===0||ids.has(row.productId))throw new Error();ids.add(row.productId);}return {items:rows.map(({productId,quantity})=>({productId,quantity})).sort((a,b)=>a.productId-b.productId),completed:Array.isArray(data.completed)?data.completed.filter(Number.isSafeInteger).slice(-100):[]};}catch{return {items:[],completed:[]};}}
  const lines=()=>state().items;
  function set(id,value){id=positiveId(id);value=quantity(value);const data=state(),rows=data.items.filter(row=>row.productId!==id);if(value)rows.push({productId:id,quantity:value});if(rows.length>100)throw new Error('Giỏ khách tối đa 100 loại sản phẩm.');storage.setItem(key,JSON.stringify({...data,items:rows.sort((a,b)=>a.productId-b.productId)}));return lines();}
  return {lines,set,add(id,value){value=quantity(value);if(!value)throw new Error('Số lượng thêm phải lớn hơn 0.');return set(id,(lines().find(row=>row.productId===positiveId(id))?.quantity||0)+value);},
    consume(purchased,orderId){const data=state();if(orderId&&data.completed.includes(orderId))return;
      const quantities=new Map(purchased.map(row=>[row.productId,row.quantity]));
      data.items=data.items.map(row=>({...row,quantity:Math.max(0,row.quantity-(quantities.get(row.productId)||0))})).filter(row=>row.quantity>0);
      if(orderId)data.completed=[...data.completed,positiveId(orderId)].slice(-100);
      storage.setItem(key,JSON.stringify(data));}};
}
