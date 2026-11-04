import { test } from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/password-reset.js', import.meta.url), 'utf8');
function setup(handler = async () => ({ ok: true, status: 200 }), token = 'A'.repeat(43)) {
  const password = { value: 'newPassword456' }, confirmation = { value: 'newPassword456' };
  const button = {}, message = {}, events = {}, calls = [], historyCalls = [];
  const form = { querySelector: () => button, reportValidity: () => true, addEventListener: (name, fn) => { events[name] = fn; } };
  const elements = { 'new-password': password, 'confirm-password': confirmation, 'reset-form': form, message };
  vm.runInNewContext(source, {
    document: { getElementById: id => elements[id] },
    location: { hash: token ? '#token=' + token : '', pathname: '/reset-password.html', search: '' },
    history: { replaceState: (...args) => historyCalls.push(args) },
    URLSearchParams, TextEncoder, AbortSignal: { timeout: () => undefined },
    fetch: async (...args) => { calls.push(args); return handler(...args); }
  });
  return { password, confirmation, button, message, form, calls, historyCalls, submit: () => events.submit({ preventDefault() {} }) };
}
test('removes fragment and rejects missing/invalid links without a request', async () => {
  for (const token of ['', 'invalid']) {
    const page = setup(undefined, token);
    assert.equal(page.historyCalls[0][2], '/reset-password.html');
    assert.equal(page.button.disabled, true); await page.submit(); assert.equal(page.calls.length, 0);
  }
});
test('mismatched confirmation never reaches the API', async () => {
  const page = setup(); page.confirmation.value = 'different'; await page.submit(); assert.equal(page.calls.length, 0);
});
test('enforces BCrypt bytes before sending', async () => {
  const page = setup(); page.password.value = page.confirmation.value = 'é'.repeat(37);
  await page.submit(); assert.equal(page.calls.length, 0); assert.match(page.message.textContent, /quá dài/);
});
test('submits exactly the token/password fields and clears secrets on success', async () => {
  const page = setup(); await page.submit(); assert.equal(page.calls[0][0], '/api/auth/reset-password');
  assert.deepEqual(JSON.parse(page.calls[0][1].body), { token: 'A'.repeat(43), newPassword: 'newPassword456', confirmPassword: 'newPassword456' });
  assert.equal(page.password.value, ''); assert.equal(page.confirmation.value, ''); assert.equal(page.form.hidden, true);
  assert.equal(page.button.disabled, true); await page.submit(); assert.equal(page.calls.length, 1);
});
test('blocks double submissions while pending', async () => {
  let release; const page = setup(() => new Promise(resolve => { release = resolve; }));
  const first = page.submit(); await page.submit(); assert.equal(page.calls.length, 1);
  release({ ok: true, status: 200 }); await first;
});
test('handles throttling safely without echoing a server payload', async () => {
  const page = setup(async () => ({ ok: false, status: 429 })); await page.submit();
  assert.match(page.message.textContent, /quá nhiều/); assert.equal(page.button.disabled, false);
  assert.equal(page.message.textContent.includes('A'.repeat(43)), false);
});
test('network uncertainty tells user to verify via login and permits retry', async () => {
  const page = setup(async () => { throw new Error('offline'); }); await page.submit();
  assert.match(page.message.textContent, /đăng nhập/); assert.equal(page.button.disabled, false);
});
