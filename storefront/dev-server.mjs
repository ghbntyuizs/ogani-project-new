import http from 'node:http';
import {readFile,realpath,stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const sourceRoot=path.dirname(fileURLToPath(import.meta.url));
const adminRoot=path.resolve(sourceRoot,'../admin');
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.jpg':'image/jpeg','.jpeg':'image/jpeg','.png':'image/png','.webp':'image/webp','.ico':'image/x-icon'};
Object.assign(types,{'.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'});
export function createDevServer(root=sourceRoot,apiTarget=process.env.OGANI_API_TARGET||'http://localhost:8080') {
  const currency=process.env.OGANI_CURRENCY||'USD';
  if(!/^[A-Z]{3}$/.test(currency))throw new Error('OGANI_CURRENCY must be a three-letter code');
  const backend=new URL(apiTarget);
  if(backend.protocol!=='http:'||!['localhost','127.0.0.1','[::1]'].includes(backend.hostname))throw new Error('Backend must be a local HTTP origin');
  return http.createServer(async(req,res)=>{
    const route=new URL(req.url,'http://localhost').pathname;
    if(['/admin/page-login.html','/page-login.html'].includes(route)){res.writeHead(302,{Location:'/login.html','Cache-Control':'no-store'});res.end();return;}
    if(route.startsWith('/api/')){
      // Browser requests are same-origin here; the fixed local upstream is server-to-server.
      const headers={...req.headers,host:backend.host};delete headers.origin;delete headers.referer;
      const upstream=http.request({hostname:backend.hostname,port:backend.port||80,method:req.method,path:req.url,headers},reply=>{res.writeHead(reply.statusCode,reply.headers);reply.pipe(res);});
      upstream.on('error',()=>{if(!res.headersSent){res.writeHead(502,{'Content-Type':'application/json'});res.end(JSON.stringify({success:false,message:'Backend chưa kết nối được. Kiểm tra server API.'}));}else res.destroy();});
      upstream.setTimeout(20000,()=>upstream.destroy());req.on('aborted',()=>upstream.destroy());req.pipe(upstream);return;
    }
    if(!['GET','HEAD'].includes(req.method)){res.writeHead(405,{Allow:'GET, HEAD'});res.end();return;}
    if(['/admin','/admin/','/admin/index.html'].includes(route)){
      res.writeHead(302,{Location:'/'+new URL(req.url,'http://localhost').search,'Cache-Control':'no-store'});res.end();return;
    }
    if(route==='/shop'||route.startsWith('/shop/')){
      const target=route==='/shop'||route==='/shop/'?'/index.html':route.slice('/shop'.length).replace(/^\/+/, '/');
      res.writeHead(302,{Location:target+new URL(req.url,'http://localhost').search,'Cache-Control':'no-store'});res.end();return;
    }
    try {
      let pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname),mount=root;const home=pathname==='/';
      if(pathname==='/'||pathname.startsWith('/admin/')){mount=adminRoot;pathname=pathname==='/'?'/index.html':pathname.slice('/admin'.length);}
      if(pathname.startsWith('/tests/')||pathname.includes('\\'))throw new Error('not found');
      if((mount===adminRoot&&pathname==='/assets/js/config.js')||(mount!==adminRoot&&pathname==='/assets/config.js')){
        res.writeHead(200,{'Content-Type':'text/javascript; charset=utf-8','Cache-Control':'no-store'});res.end(req.method==='HEAD'?null:"window.OGANI_API_BASE='';window.OGANI_STOREFRONT_CONFIG="+JSON.stringify({apiBase:'',currency})+';');return;
      }
      let file=path.resolve(mount,'.'+(pathname.endsWith('/')?pathname+'index.html':pathname));
      const relative=path.relative(mount,file);
      if(relative.startsWith('..')||path.isAbsolute(relative))throw new Error('not found');
      const [actualRoot,actualFile]=await Promise.all([realpath(mount),realpath(file)]);
      const actualRelative=path.relative(actualRoot,actualFile);
      if(actualRelative.startsWith('..')||path.isAbsolute(actualRelative))throw new Error('not found');
      file=actualFile;const info=await stat(file),type=types[path.extname(file).toLowerCase()];
      if(!info.isFile()||!type)throw new Error('not found');
      let content=await readFile(file);
      if(home&&mount===adminRoot)content=Buffer.from(content.toString().replace('<head>','<head>\n  <base href="/admin/">'));
      res.writeHead(200,{'Content-Type':type,'Content-Length':content.length,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(req.method==='HEAD'?null:content);
    } catch {res.writeHead(404,{'Content-Type':'text/plain; charset=utf-8'});res.end('Not found');}
  });
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const port=Number(process.env.STOREFRONT_PORT||5500);
  if(!Number.isInteger(port)||port<1||port>65535)throw new Error('Invalid STOREFRONT_PORT');
  const server=createDevServer();
  server.on('error',error=>{console.error(error.code==='EADDRINUSE'?`Port ${port} is in use. Stop the old frontend server first.`:error.message);process.exitCode=1;});
  server.listen(port,'127.0.0.1',()=>console.log(`Ogani web: http://localhost:${port}/login.html (Admin: /, Store: /index.html)`));
  process.on('SIGINT',()=>server.close(()=>process.exit(0)));
}
