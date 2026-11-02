import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

export async function run() {
  const source = await readFile(new URL('../assets/js/product-editor.js', import.meta.url), 'utf8');
  const context = { window: {} }; vm.runInNewContext(source, context);
  const Editor = context.window.OganiProductEditor;
  let calls = [];
  const api = { request: async (path, options) => {
    calls.push({ path, options });
    return path.endsWith('/state') ? { productId: 1, version: 7, thumbnailUrl: 'old.png', images: [] }
      : { id: 1, version: 8, thumbnailUrl: 'new.png' };
  } };
  let editor = new Editor(api, { id: 1, version: 7, thumbnailUrl: 'old.png' });
  await editor.save({ name: 'Changed', version: 999 });
  assert.equal(calls[0].options.body.version, 7, 'a caller cannot replace the form version');
  assert.equal(editor.version, 8);

  calls = [];
  editor = new Editor({ request: async (path, options) => {
    calls.push({ path, options });
    return { productId: 1, version: 9, thumbnailUrl: 'selected.png', images: [{ id: 42 }] };
  } }, { id: 1, version: 7 });
  await editor.addImage('/api/images/new.png', true);
  assert.equal(calls.length, 1, 'gallery mutation must not fetch product detail afterwards');
  assert.ok(calls[0].path.endsWith('/images/state?thumbnail=true'));
  assert.equal(calls[0].options.headers['If-Match'], '7');
  assert.equal(editor.version, 9);
  await editor.selectThumbnail(42);
  assert.ok(calls[1].path.endsWith('/state/42/thumbnail'));
  assert.equal(calls[1].options.headers['If-Match'], '9');

  editor = new Editor({ request: async () => ({ productId: 1, version: 8, images: [] }) }, { id: 1, version: 7 });
  await assert.rejects(editor.loadGallery(), error => error.status === 409);
  assert.equal(editor.version, 7, 'a newer GET must never silently authorize an old form');
  assert.equal(editor.stale, true);

  let attempts = 0;
  editor = new Editor({ request: async () => { attempts++; const e = new Error('Conflict'); e.status = 409; throw e; } }, { id: 1, version: 7 });
  await assert.rejects(editor.save({ name: 'Old data' }));
  await assert.rejects(editor.save({ name: 'Retry old data' }));
  assert.equal(attempts, 1, 'a stale form must reload rather than automatically retry');

  let release;
  editor = new Editor({ request: () => new Promise(resolve => { release = resolve; }) }, { id: 1, version: 7 });
  const pending = editor.selectThumbnail(42);
  await assert.rejects(editor.removeImage(42), /wait/i);
  release({ productId: 1, version: 8, thumbnailUrl: 'x', images: [] }); await pending;

  return 'Product editor tests: 6 scenarios passed';
}
