import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

class Headers {
  constructor(values = {}) { this.values = new Map(Object.entries(values)); }
  set(key, value) { this.values.set(key, value); }
  get(key) { return this.values.get(key); }
}
class FormData {}
class Response {
  constructor(body, {status}) { this.body = body; this.status = status; this.ok = status < 400; }
  async json() { return JSON.parse(this.body); }
}
const AbortSignal = { timeout() { return undefined; } };

export async function run() {
  const source = await readFile(new URL('../assets/js/api.js', import.meta.url), 'utf8');
  function setup(handler, navigator = {}) {
    const values = new Map();
    const context = {
      window: {}, OGANI_API_BASE: 'http://localhost:8080', navigator,
      sessionStorage: { getItem: k => values.get(k) ?? null, setItem: (k,v) => values.set(k,v), removeItem: k => values.delete(k) },
      location: { href: 'http://localhost:5500/admin/products.html', assign() {} },
      fetch: handler, Headers, FormData, URL, AbortSignal, setTimeout, clearTimeout
    };
    context.window = context;
    vm.runInNewContext(source, context);
    return context.OganiAPI;
  }
  const ok = data => new Response(JSON.stringify({ success: true, data }), { status: 200 });
  const session = { accessToken: 'old', refreshToken: 'refresh-old', user: { role: 'ADMIN' } };
  let calls = [];
  let api = setup(async (url, init) => { calls.push([url, init]); return ok({ id: 1 }); });
  api.setSession(session);
  assert.equal((await api.request('/api/admin/products')).id, 1);
  assert.equal(calls[0][1].headers.get('Authorization'), 'Bearer old');

  let rotations = 0;
  api = setup(async (url, init) => {
    if (url.endsWith('/refresh')) { rotations++; await new Promise(r => setTimeout(r, 5)); return ok({ ...session, accessToken: 'new' }); }
    return init.headers.get('Authorization') === 'Bearer new' ? ok('loaded') : new Response('{}', { status: 401 });
  });
  api.setSession(session);
  assert.deepEqual(await Promise.all([api.request('/api/admin/products'), api.request('/api/admin/categories')]), ['loaded', 'loaded']);
  assert.equal(rotations, 1, 'concurrent requests must share one refresh');

  api = setup(async () => new Response(JSON.stringify({ message: 'Denied', details: [{ field: 'sku', message: 'Required' }] }), { status: 403 }));
  await assert.rejects(api.request('/api/admin/products'), error => error.status === 403 && error.message.includes('sku'));

  api = setup(async () => new Response('{}', { status: 401 }));
  api.setSession(session);
  await assert.rejects(api.request('/api/admin/products'));
  assert.equal(api.session(), null);
  assert.equal(api.imageUrl('javascript:alert(1)'), '');
  assert.equal(api.imageUrl('/api/images/a.png'), 'http://localhost:8080/api/images/a.png');
  assert.equal(api.imageUrl('/api/images/a.png', true), 'http://localhost:8080/api/images/a.png?size=thumb');
  assert.equal(api.imageUrl('https://cdn.example.com/a.png', true), 'https://cdn.example.com/a.png');
  api = setup(async () => ok('unexpected'), { locks: { request: async (_name, _options, callback) => callback(null) } });
  api.setSession(session);
  await assert.rejects(api.request('/api/admin/products'), /another tab/i);
  assert.equal(api.session(), null, 'a copied session must not share single-use refresh tokens');
  return 'Admin API tests: 6 scenarios passed';
}
