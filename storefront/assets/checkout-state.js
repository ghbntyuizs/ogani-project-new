export function createCheckoutState({storage,userId,request,createKey,prefix='ogani.customer.checkout.',endpoint='/api/checkout',onConfirmed}) {
  let busy=false;
  const key=()=>{const id=userId();if(!id)throw Object.assign(new Error('Vui lòng đăng nhập để đặt hàng.'),{status:401});return prefix+id;};
  function pending() {
    try {const data=JSON.parse(storage.getItem(key())||'null');return data&&data.userId===userId()&&typeof data.key==='string'&&data.body?.cartToken?data:null;}
    catch {return null;}
  }
  function clear() {storage.removeItem(key());}
  function clearAttempt(attempt){const name=prefix+attempt.userId;try{if(JSON.parse(storage.getItem(name)||'null')?.key===attempt.key)storage.removeItem(name);}catch{}}
  async function execute(attempt) {
    if(busy)throw new Error('Yêu cầu đặt hàng đang được xử lý.');busy=true;
    try {
      const order=await request(endpoint,{method:'POST',headers:{'Idempotency-Key':attempt.key},body:attempt.body});
      if(!Number.isSafeInteger(order?.id)||order.id<=0)throw Object.assign(new Error('Chưa xác nhận được kết quả đặt hàng. Vui lòng thử lại cùng yêu cầu.'),{uncertain:true});
      if(onConfirmed)await onConfirmed(order,attempt.body);
      // Clear only the customer whose request completed; never another customer's attempt.
      clearAttempt(attempt);return order;
    } catch(error) {
      if(!error.uncertain&&[400,404,409,422].includes(error.status))clearAttempt(attempt);
      throw error;
    } finally {busy=false;}
  }
  return {pending,clear,
    async place(body) {
      if(busy)throw new Error('Yêu cầu đặt hàng đang được xử lý.');
      let attempt=pending();
      if(attempt&&JSON.stringify(attempt.body)!==JSON.stringify(body))throw new Error('Hãy kiểm tra yêu cầu đang chờ trước khi thay đổi đơn hàng.');
      if(!attempt){attempt={userId:userId(),key:createKey(),body:JSON.parse(JSON.stringify(body))};storage.setItem(key(),JSON.stringify(attempt));}
      return execute(attempt);
    },
    async retry() {const attempt=pending();if(!attempt)throw new Error('Không có yêu cầu đặt hàng đang chờ.');return execute(attempt);}
  };
}
