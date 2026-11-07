import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createDevServer} from '../dev-server.mjs';

test('local server serves modules without exposing parent files or development tests',async()=>{
  const server=createDevServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  try {
    assert.equal((await fetch(base+'/')).status,200);
    const module=await fetch(base+'/assets/api.js');assert.equal(module.status,200);assert.match(module.headers.get('content-type'),/javascript/);
    for(const value of ['/tests/api.test.mjs','/package.json','/dev-server.mjs','/%2e%2e%5cbackend%5cpom.xml'])assert.equal((await fetch(base+value)).status,404);
    assert.equal((await fetch(base+'/',{method:'POST'})).status,405);
  } finally {await new Promise(resolve=>server.close(resolve));}
});

test('unified host keeps root admin and explicit index storefront with isolated mounts',async()=>{
  const server=createDevServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  try {
    assert.match(await (await fetch(base+'/')).text(),/Dashboard - Ogani Admin/);
    assert.match(await (await fetch(base+'/')).text(),/<base href="\/admin\/">/);
    assert.match(await (await fetch(base+'/index.html')).text(),/data-page="catalog"/);
    assert.match(await (await fetch(base+'/admin/orders.html')).text(),/Orders/);
    assert.equal((await fetch(base+'/shop/assets/api.js')).status,200);
    assert.equal((await fetch(base+'/admin/assets/js/api.js')).status,200);
    for(const url of ['/admin/tests/api.test.mjs','/admin/%2e%2e%5cbackend%5cpom.xml','/backend/pom.xml'])assert.equal((await fetch(base+url)).status,404);
  } finally {await new Promise(resolve=>server.close(resolve));}
});

test('unified host proxies authenticated API writes to a fixed backend',async()=>{
  const upstream=(await import('node:http')).createServer(async(req,res)=>{let body='';for await(const chunk of req)body+=chunk;res.setHeader('Content-Type','application/json');res.end(JSON.stringify({url:req.url,auth:req.headers.authorization,body}));});
  await new Promise(resolve=>upstream.listen(0,'127.0.0.1',resolve));
  const server=createDevServer(undefined,'http://127.0.0.1:'+upstream.address().port);await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{
    const response=await fetch('http://127.0.0.1:'+server.address().port+'/api/admin/orders/1/status',{method:'PATCH',headers:{Authorization:'Bearer test','Content-Type':'application/json'},body:'{"status":"CONFIRMED","version":0}'});
    assert.deepEqual(await response.json(),{url:'/api/admin/orders/1/status',auth:'Bearer test',body:'{"status":"CONFIRMED","version":0}'});
  }finally{await new Promise(resolve=>server.close(resolve));await new Promise(resolve=>upstream.close(resolve));}
});

test('old admin login URLs redirect to the single storefront login',async()=>{
  const server=createDevServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{for(const path of ['/admin/page-login.html','/page-login.html']){const response=await fetch('http://127.0.0.1:'+server.address().port+path,{redirect:'manual'});assert.equal(response.status,302);assert.equal(response.headers.get('location'),'/login.html');}}
  finally{await new Promise(resolve=>server.close(resolve));}
});

test('compatibility storefront paths redirect to one canonical set of modules',async()=>{
  const server=createDevServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{for(const [path,target] of [['/shop/','/index.html'],['/shop/assets/api.js','/assets/api.js'],['/shop/login.html?reason=expired','/login.html?reason=expired']]){const response=await fetch('http://127.0.0.1:'+server.address().port+path,{redirect:'manual'});assert.equal(response.status,302);assert.equal(response.headers.get('location'),target);}}
  finally{await new Promise(resolve=>server.close(resolve));}
});

test('admin entry bookmarks use the canonical dashboard without a missing page',async()=>{
  const server=createDevServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{for(const [path,target] of [['/admin','/'],['/admin/','/'],['/admin/index.html','/'],['/admin?from=bookmark','/?from=bookmark']]){const response=await fetch('http://127.0.0.1:'+server.address().port+path,{redirect:'manual'});assert.equal(response.status,302);assert.equal(response.headers.get('location'),target);const resolved=await fetch('http://127.0.0.1:'+server.address().port+path);assert.equal(resolved.status,200);assert.match(await resolved.text(),/Dashboard - Ogani Admin/);}}
  finally{await new Promise(resolve=>server.close(resolve));}
});

test('legacy bookmark normalization cannot redirect to an external host',async()=>{
  const server=createDevServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port;
  try{const response=await fetch(base+'/shop//outside.example/login.html',{redirect:'manual'});assert.equal(response.status,302);assert.equal(new URL(response.headers.get('location'),base).origin,base);}
  finally{await new Promise(resolve=>server.close(resolve));}
});
